package keepalive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AliveKeeper {

    private final static Logger logger = LoggerFactory.getLogger(AliveKeeper.class);

    private ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> keepAliveFuture;
    private ScheduledFuture<?> deadlineFuture;

    private final Map<Long, KeepAlive> keepAliveMap;
    private final AtomicLong currentKeepAliveId;

    private final AtomicInteger start;

    public AliveKeeper() {
        keepAliveMap = new ConcurrentHashMap<>();
        currentKeepAliveId = new AtomicLong(0L);
        start = new AtomicInteger(0);
    }

    public Long applyKeepAliveId() {
        return currentKeepAliveId.incrementAndGet();
    }

    public KeepAliveListener keepAlive(long keepAliveId, HeartBeat heartBeat, HeartBeatTransport hbTransport) {
        checkNotNull(heartBeat, hbTransport);
        KeepAlive keepAlive = keepAliveMap.computeIfAbsent(keepAliveId, (k) -> {
            KeepAlive keepAlive0 = new KeepAlive(keepAliveId, heartBeat, hbTransport);
            long currentTime = System.currentTimeMillis();
            keepAlive0.nextHBTime = currentTime + heartBeat.getTTL() * 1000 / 3;
            keepAlive0.deadline = currentTime + heartBeat.getTTL();
            return keepAlive0;
        });
        if(start.get() == 0 && start.compareAndSet(0, 1)) {
            startKeepAliveExecutor();
            startDeadlineExecutor();
        }
        return keepAlive.keepAliveListener;
    }

    private void startKeepAliveExecutor() {
        keepAliveFuture = scheduledExecutor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            keepAliveMap.values().stream()
                    .filter(keepAlive -> keepAlive.heartBeat.isActiveType() && keepAlive.nextHBTime <= now)
                    .forEach(KeepAlive::doHeartbeat);
        }, 0, 500L, TimeUnit.MILLISECONDS);
    }

    private void startDeadlineExecutor() {
        deadlineFuture = scheduledExecutor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            keepAliveMap.values().removeIf(keepAlive -> {
                if(keepAlive.deadline <= now) {
                    keepAlive.close();
                    return true;
                }
                return false;
            });
        }, 0, 500L, TimeUnit.MILLISECONDS);
    }

    private void removeKeepAlive(long keepAliveId) {
        keepAliveMap.remove(keepAliveId);
    }

    private void processHBResponse(long keepAliveId, String response) {
        KeepAlive keepAlive = checkTimeUp(keepAliveId);
        if(keepAlive == null) return;

        long now = System.currentTimeMillis();
        HeartBeat heartBeat = keepAlive.heartBeat;
        keepAlive.deadline = now + heartBeat.getTTL();
        keepAlive.nextHBTime = now + heartBeat.getTTL() * 1000 / 3;

        HeartBeat responseHeartBeat = new HeartBeat(null, response, keepAlive.deadline - now);
        keepAlive.notifyListener(responseHeartBeat);
    }

    private void processHBRequest(long keepAliveId, String request) {
        KeepAlive keepAlive = checkTimeUp(keepAliveId);
        if(keepAlive == null) return;

        long now = System.currentTimeMillis();

        HeartBeat heartBeat = keepAlive.heartBeat;
        keepAlive.deadline = now + heartBeat.getTTL();

        HeartBeat requestHeartBeat = new HeartBeat(request, null, keepAlive.deadline - now);
        keepAlive.notifyListener(requestHeartBeat);
    }

    private KeepAlive checkTimeUp(long keepAliveId) {
        KeepAlive keepAlive = keepAliveMap.get(keepAliveId);
        if(keepAlive == null) return null;

        long now = System.currentTimeMillis();

        if(keepAlive.deadline - now <= 0) {
            logger.debug("[{}] Time up!", keepAliveId);
            keepAliveMap.remove(keepAliveId);
            HeartBeat exceptionHeartBeat = new HeartBeat(new HeartBeatException("Time up!"));
            keepAlive.notifyListener(exceptionHeartBeat);
            return null;
        }
        return keepAlive;
    }

    private void checkNotNull(HeartBeat heartBeat, HeartBeatTransport hbTransport) {
        if(heartBeat == null || heartBeat.getTTL() < 1
                || heartBeat.getRequest() == null
                || heartBeat.getResponse() == null
                || heartBeat.isActiveType() && heartBeat.getEncodeRequest() == null) {
            //logger.error("");
            throw new IllegalArgumentException("invalid HeartBeat!");
        }
        if(hbTransport == null) {
            throw new IllegalArgumentException("invalid HeartBeatTransport!");
        }
    }

    private class KeepAlive {

        private HeartBeat heartBeat;
        private HeartBeatTransport heartBeatTransport;

        private long nextHBTime;
        private long deadline;

        private KeepAliveListenerImpl keepAliveListener;
        private HeartBeatFilter heartBeatFilter;

        KeepAlive(long keepAliveId, HeartBeat heartBeat, HeartBeatTransport hbTransport) {
            this.heartBeat = heartBeat;
            this.heartBeatTransport = hbTransport;
            this.keepAliveListener = new KeepAliveListenerImpl(keepAliveId);
            this.heartBeatFilter = new HeartBeatFilterImpl(keepAliveId, this);
        }

        void notifyListener(HeartBeat inputHeartBeat) {
            keepAliveListener.enqueue(inputHeartBeat);
        }

        void doHeartbeat() {
            try {
                heartBeatTransport.receive(heartBeatFilter);
                heartBeatTransport.send(heartBeat.getEncodeRequest());
            } catch (Exception e) {
                logger.error("do heartbeat failed, keepAliveId[{}]");
                HeartBeatException heartBeatException = new HeartBeatException(e);
                HeartBeat exceptionHeartBeat = new HeartBeat(heartBeatException);
                notifyListener(exceptionHeartBeat);
            }
        }

        void close() {
            try { heartBeatTransport.destroy(); } catch (Exception e) { logger.error("", e); }
            keepAliveListener.close();
        }

    }

    private class HeartBeatFilterImpl implements HeartBeatFilter {

        private HeartBeat heartBeat;
        private long keepAliveId;

        HeartBeatFilterImpl(long keepAliveId, KeepAlive keepAlive) {
            this.keepAliveId = keepAliveId;
            this.heartBeat = keepAlive.heartBeat;
        }

        @Override
        public boolean filter(String receivedMsg) {
            if(heartBeat.isActiveType()) {
                if(heartBeat.getResponse().equals(receivedMsg)) {
                    processHBResponse(keepAliveId, receivedMsg);
                    return true;
                }
            } else {
                if(heartBeat.getRequest().equals(receivedMsg)) {
                    processHBRequest(keepAliveId, receivedMsg);
                    return true;
                }
            }
            return false;
        }
    }

    private class KeepAliveListenerImpl implements KeepAliveListener {

        private long keepAliveId;

        private volatile boolean close;

        private BlockingQueue<HeartBeat> queue = new ArrayBlockingQueue<>(1);

        private ExecutorService executor = Executors.newSingleThreadExecutor();

        KeepAliveListenerImpl(long keepAliveId) {
            this.keepAliveId = keepAliveId;
        }

        private void enqueue(HeartBeat inputHeartBeat) {
            if(close) return;
            if(inputHeartBeat.getHeartBeatException() != null)
                queue.clear();
            queue.offer(inputHeartBeat);
        }

        @Override
        public HeartBeat listen() throws InterruptedException {
            if(close) {
                throw new IllegalStateException("Listener has bean closed!");
            }
            Future<HeartBeat> future = executor.submit(() -> queue.take());
            HeartBeat heartBeat = null;
            //todo ExecutionException???
            try {
                heartBeat = future.get();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            return heartBeat;
        }

        @Override
        public void close() {
            //notify listen
            removeKeepAlive(keepAliveId);
            close = true;
        }
    }

}
