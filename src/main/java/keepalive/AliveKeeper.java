package keepalive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * We start two threads, one to actively send heartbeat requests (TTL /3 intervals) and one to terminate the heartbeat.
 * When we receive a heartbeat response, we update its deadline and the next heartbeat request time.
 * When we receive a heartbeat request, we update its deadline and respond to a heartbeat.
 */
public class AliveKeeper {

    private final static Logger logger = LoggerFactory.getLogger(AliveKeeper.class);

    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2);

    private final Map<Long, KeepAlive> keepAliveMap;
    private final AtomicLong currentKeepAliveId;

    //0: default, -1: stopped, 1: started
    private int state = 0;

    public AliveKeeper() {
        keepAliveMap = new ConcurrentHashMap<>();
        currentKeepAliveId = new AtomicLong(0L);
    }

    public Long applyKeepAliveId() {
        return currentKeepAliveId.incrementAndGet();
    }

    public synchronized KeepAliveListener keepAlive(long keepAliveId, HeartBeat heartBeat, HeartBeatTransport heartBeatTransport) {
        if(state < 0)
            throw new IllegalStateException("stopped!");
        checkValid(heartBeat, heartBeatTransport);
        KeepAlive keepAlive = keepAliveMap.computeIfAbsent(keepAliveId, (k) -> {
            KeepAlive keepAlive0 = new KeepAlive(keepAliveId, heartBeat, heartBeatTransport);
            heartBeatTransport.addObserver(new HeartBeatObserverImpl(keepAliveId, heartBeat));
            long now = System.currentTimeMillis();
            keepAlive0.nextHBTime = now + heartBeat.getTTL() / 3;
            keepAlive0.deadline = now + heartBeat.getTTL();
            return keepAlive0;
        });
        if(state == 0) start();
        return keepAlive.keepAliveListener;
    }

    public synchronized void stop() {
        scheduledExecutor.shutdownNow();
        Iterator<KeepAlive> it = keepAliveMap.values().iterator();
        for (; it.hasNext(); ) {
            it.next().close();
            it.remove();
        }
        state = -1;
        logger.info("stopped...");
    }

    private void start() {
        startKeepAliveExecutor();
        startDeadlineExecutor();
        state = 1;
        logger.info("started...");
    }

    private void startKeepAliveExecutor() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            keepAliveMap.values().stream()
                    .filter(keepAlive -> keepAlive.heartBeat.isRequestPacket()
                            && keepAlive.nextHBTime <= now
                            && keepAlive.deadline > now)
                    .forEach(KeepAlive::doRequest);
        }, 500L, 500L, TimeUnit.MILLISECONDS);
    }

    private void startDeadlineExecutor() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            keepAliveMap.values().removeIf(keepAlive -> {
                if (keepAlive.deadline <= now) {
                    //only too long without heartbeat we will call destroy
                    if (keepAlive.deadline > 0) keepAlive.destroy();
                    keepAlive.close();
                    logger.debug("Time up! remove happen...");
                    return true;
                }
                return false;
            });
        }, 1000L, 1000L, TimeUnit.MILLISECONDS);
    }

    private void removeKeepAlive(KeepAlive keepAlive) {
        //lazy remove
        keepAlive.deadline = -1;
    }

    private void processHBResponse(long keepAliveId, String response) {
        KeepAlive keepAlive = checkTimeUp(keepAliveId);
        if(keepAlive == null) return;

        long now = System.currentTimeMillis();

        HeartBeat heartBeat = keepAlive.heartBeat;
        long deadline = keepAlive.deadline;
        keepAlive.deadline = now + heartBeat.getTTL();
        keepAlive.nextHBTime = now + heartBeat.getTTL() / 3;

        HeartBeat res = HeartBeat.buildResponse(response, deadline - now);
        keepAlive.notifyListener(res);
    }

    private void processHBRequest(long keepAliveId, String request) {
        KeepAlive keepAlive = checkTimeUp(keepAliveId);
        if(keepAlive == null) return;

        long now = System.currentTimeMillis();

        HeartBeat heartBeat = keepAlive.heartBeat;
        long deadline = keepAlive.deadline;
        //only update deadline
        keepAlive.deadline = now + heartBeat.getTTL();
        //send heartbeat response
        keepAlive.doResponse();

        HeartBeat req = HeartBeat.buildRequest(request, deadline - now);
        keepAlive.notifyListener(req);
    }

    private KeepAlive checkTimeUp(long keepAliveId) {
        KeepAlive keepAlive = keepAliveMap.get(keepAliveId);
        if(keepAlive == null) return null;

        long now = System.currentTimeMillis();

        if(keepAlive.deadline - now <= 0) {
            logger.debug("Time up! keepAliveId[{}]", keepAliveId);
            HeartBeat ehb = HeartBeat.buildException(new HeartBeatException("Time up!"));
            keepAlive.notifyListener(ehb);
            return null;
        }
        return keepAlive;
    }

    private void checkValid(HeartBeat heartBeat, HeartBeatTransport hbTransport) {
        if(heartBeat == null || heartBeat.getTTL() < 1200
                || heartBeat.isRequestPacket() && (heartBeat.getEncodeRequest() == null || heartBeat.getResponse() == null)
                || !heartBeat.isRequestPacket() && (heartBeat.getEncodeResponse() == null || heartBeat.getRequest() == null)) {
            throw new IllegalArgumentException("invalid HeartBeat!");
        }
        if(hbTransport == null) {
            throw new IllegalArgumentException("invalid HeartBeatTransport!");
        }
    }

    private class KeepAlive {

        //private long keepAliveId;

        private HeartBeat heartBeat;
        private HeartBeatTransport heartBeatTransport;

        private long nextHBTime;
        private long deadline;

        private KeepAliveListenerImpl keepAliveListener;

        KeepAlive(long keepAliveId, HeartBeat heartBeat, HeartBeatTransport heartBeatTransport) {
            //this.keepAliveId = keepAliveId;
            this.heartBeat = heartBeat;
            this.heartBeatTransport = heartBeatTransport;
            this.keepAliveListener = new KeepAliveListenerImpl(this);
        }

        void notifyListener(HeartBeat inputHeartBeat) {
            keepAliveListener.enqueue(inputHeartBeat);
        }

        void doRequest() {
            doHeartbeat(heartBeat.getEncodeRequest());
        }

        void doResponse() {
            doHeartbeat(heartBeat.getEncodeResponse());
        }

        void close() {
            keepAliveListener.close();
        }

        void destroy() {
            try { heartBeatTransport.destroy(); } catch (Exception e) { logger.error("", e); }
        }

        private void doHeartbeat(byte[] hbBytes) {
            try {
                heartBeatTransport.send(hbBytes);
            } catch (Exception e) {
                logger.error("do heartbeat failed, keepAliveId[{}]");
                HeartBeat ehb = HeartBeat.buildException(new HeartBeatException(e));
                notifyListener(ehb);
            }
        }

    }

    private class HeartBeatObserverImpl implements HeartBeatObserver {

        private HeartBeat heartBeat;
        private long keepAliveId;

        HeartBeatObserverImpl(long keepAliveId, HeartBeat heartBeat) {
            this.keepAliveId = keepAliveId;
            this.heartBeat = heartBeat;
        }

        @Override
        public boolean update(String msg) {
            if(heartBeat.isRequestPacket()) {
                if(heartBeat.getResponse().equals(msg)) {
                    processHBResponse(keepAliveId, msg);
                    return true;
                }
            } else {
                if(heartBeat.getRequest().equals(msg)) {
                    processHBRequest(keepAliveId, msg);
                    return true;
                }
            }
            return false;
        }
    }

    private class KeepAliveListenerImpl implements KeepAliveListener {

        private KeepAlive keepAlive;

        private boolean close;

        private BlockingQueue<HeartBeat> queue = new ArrayBlockingQueue<>(1);

        private ExecutorService executor;

        KeepAliveListenerImpl(KeepAlive keepAlive) {
            this.keepAlive = keepAlive;
        }

        private void enqueue(HeartBeat inputHeartBeat) {
            if(close) return;
            if(inputHeartBeat.getHeartBeatException() != null)
                queue.clear();
            queue.offer(inputHeartBeat);
        }

        @Override
        public synchronized HeartBeat listen() {
            if(close) {
                throw new IllegalStateException("Listener has bean closed!");
            }
            if(executor == null) {
                executor = Executors.newSingleThreadExecutor();
            }
            Future<HeartBeat> future = executor.submit(() -> queue.take());
            HeartBeat heartBeat = null;
            try {
                heartBeat = future.get();
            } catch (ExecutionException | InterruptedException e) {
                //logger.error("", e);
            }
            return heartBeat;
        }

        @Override
        public synchronized void close() {
            if(!close) {
                removeKeepAlive(keepAlive);
                if(executor != null) executor.shutdownNow();
                close = true;
            }
        }
    }

}
