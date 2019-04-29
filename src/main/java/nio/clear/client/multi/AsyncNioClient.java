package nio.clear.client.multi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncNioClient {

    private Logger logger = LoggerFactory.getLogger(AsyncNioClient.class);

    private Selector selector;

    private Thread own;
    private AtomicBoolean start = new AtomicBoolean(false);

    private final Map<String, NioConnection> registerTable = new ConcurrentHashMap<>();

    private final int defaultProbeInterval = 3000;

    private final long SELECT_TIMEOUT = 1000L;

    private long lastCheckIdleTime;

    public AsyncNioClient() {
        try {
            selector = Selector.open();
        } catch (IOException ioe) {
            throw new RuntimeException();
        }
    }

    public void start() {
        if(!start.compareAndSet(false, true)) return;
        own = new Thread(() -> {
            try {
                while (!Thread.interrupted()) {

                    int event = selector.select(SELECT_TIMEOUT);

                    if(event > 0) {
                        Set<SelectionKey> keySet = selector.selectedKeys();
                        for(Iterator<SelectionKey> it = keySet.iterator(); it.hasNext();) {
                            SelectionKey key = it.next();
                            NioConnection connection = (NioConnection) key.attachment();
                            connection.doIo();
                            it.remove();
                        }
                    }

                    checkIdleConn();
                }
            } catch (Exception e) {
                logger.error("", e);
            } finally {
                start.set(false);
                release();
            }
        });
        own.start();
    }

    public void stop() {
        if(own != null)
            own.interrupt();
        logger.info("async client stopped");
    }

    private void checkIdleConn() {
        long currentTime = System.currentTimeMillis();
        if(currentTime - lastCheckIdleTime >= SELECT_TIMEOUT) {
            lastCheckIdleTime = currentTime;
            synchronized (registerTable) {
                for (NioConnection connection : registerTable.values()) {
                    connection.checkIdle(currentTime);
                }
            }
        }
    }

    private String makeKey(String host, int port) {
        return host + ":" + port;
    }

    public void deregister(String host, int port) {
        final String hpKey = makeKey(host, port);
        NioConnection connection;
        synchronized (registerTable) {
            connection = registerTable.remove(hpKey);
        }
        connection.disabled();
        connection.disconnect(null);
    }

    //thread unsafe
    public void register(String host, int port, IOListener listener, String probe, int probeInterval) {
        final String hpKey = makeKey(host, port);
        if (registerTable.get(hpKey) != null)
            throw new IllegalStateException("host[" + host + ":" + port + "] has been registered");

        if(probe == null) {
            throw new IllegalArgumentException("probe is not allow null");
        }

        if(probeInterval < defaultProbeInterval) {
            probeInterval = defaultProbeInterval;
        }

        NioConnection connection = new NioConnection(host, port, selector, listener, probe, probeInterval);
        synchronized (registerTable) {
            registerTable.put(hpKey, connection);
        }
    }

    private void release() {
        closeSelector();
        registerTable.clear();
    }

    private void closeSelector() {
        if(selector != null && selector.isOpen()) {
            try {
                selector.close();
            } catch (IOException ioe) {
                logger.error("", ioe);
            }
        }
        selector = null;
    }

}
