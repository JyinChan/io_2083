package nio.clear.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;

public class Reactor {

    private final static Logger logger = LoggerFactory.getLogger(Reactor.class);

    private final static long SELECT_TIMEOUT = 1000L;

    private final static ExecutorService executor =
            new ThreadPoolExecutor(0, 2048, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());

    private Thread reactorThread;

    private Selector selector;

    private long lastCheckIdleTime;

    //channel.register maybe cause block if it invoked by non-reactor thread, so we need a queue for reactor to process
    private Queue<Object> registerQueue = new ConcurrentLinkedQueue<>();
    //key.interestOps maybe cause block in some run environment
    private Queue<NioConnection> applyQueue = new ConcurrentLinkedQueue<>();

    public Reactor() {
        try {
            selector = Selector.open();
        } catch (IOException ioe) {
            logger.error("", ioe);
            throw new RuntimeException();
        }
    }

    public static void execute(Runnable r) {
        executor.execute(r);
    }

    public boolean isReactorThread() {
        return reactorThread == Thread.currentThread();
    }

    public RegFuture connect(String ip, int port, Class<? extends NioConnection> connType, IoListener ioListener) {
        RegFuture regFuture = new RegFuture();
        NioConnection connection;
        SocketChannel channel = null;
        try {
            channel = SocketChannel.open();
            channel.socket().setTcpNoDelay(true);
            channel.configureBlocking(false);
            //non-block connect
            channel.connect(new InetSocketAddress(ip, port));

            Constructor<? extends NioConnection> constructor = connType.getDeclaredConstructor(Reactor.class, SocketChannel.class, IoListener.class);
            connection = constructor.newInstance(this, channel, ioListener);
        } catch (Exception e) {
            logger.error("", e);
            close(channel);
            regFuture.done(null);
            return regFuture;
        }

        connection.regFuture = regFuture;
        registerQueue.add(connection);
        selector.wakeup();
        return regFuture;
    }

    public boolean addBind(String ip, int port, int backlog, Class<? extends NioConnection> connType, Class<? extends IoListener> listenerClass) {
        //register acceptor
        Acceptor acceptor;
        ServerSocketChannel serverChannel = null;
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(ip, port), backlog);
            acceptor = new Acceptor(serverChannel, connType, listenerClass);
        } catch (Exception e) {
            logger.error("", e);
            close(serverChannel);
            return false;
        }

        registerQueue.add(acceptor);
        selector.wakeup();
        return true;
    }

    public void start() {
        if(reactorThread != null) return;
        reactorThread = new Thread(() -> {

            while (true) {
                int readyChannels = 0;
                try {
                    readyChannels = selector.select(SELECT_TIMEOUT);
                    System.out.println("select wake " + readyChannels);
                } catch (IOException ioe) {
                    //I don't know how it perform(normal or not in next select) after a IOException thrown.
                    try { Thread.sleep(SELECT_TIMEOUT); } catch (InterruptedException ignore) {}
                    logger.error("", ioe);
                }
                long currentTime = System.currentTimeMillis();
                if (readyChannels > 0) {
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    for (Iterator<SelectionKey> it = selectedKeys.iterator(); it.hasNext(); ) {
                        SelectionKey key = it.next();
                        Processable p = (Processable) key.attachment();
                        p.process(currentTime, false);
                        it.remove();
                    }
                }

                doRegister();

                doProcess(currentTime);

                notifyIdleConn(currentTime);
            }
        }, "Reactor_Thread");
        reactorThread.start();
    }

    void applyProcess(NioConnection connection) {
        if(connection.setApplyMark(true)) {
            applyQueue.add(connection);
            selector.wakeup();
        }
    }

    private void doProcess(long currentTime) {
        if(applyQueue.isEmpty())
            return;
        for( ; ;) {
            NioConnection connection = applyQueue.poll();
            if(connection == null)
                break;
            connection.setApplyMark(false);
            connection.process(currentTime, true);
        }
    }

    private void notifyIdleConn(long currentTime) {
        if(currentTime - lastCheckIdleTime >= SELECT_TIMEOUT) {
            lastCheckIdleTime = currentTime;
            for (SelectionKey key : selector.keys()) {
                if (key.isValid()) {
                    Object o = key.attachment();
                    if(o instanceof NioConnection) {
                        NioConnection connection = (NioConnection) key.attachment();
                        connection.notifyIdle(currentTime);
                    }
                }
            }
        }
    }

    private void doRegister() {
        if(registerQueue.isEmpty())
            return;
        for( ; ;) {
            Object o = registerQueue.poll();
            if(o == null)
                break;
            if(o instanceof Acceptor) {
                Acceptor acceptor = (Acceptor) o;
                register(acceptor.serverChannel, SelectionKey.OP_ACCEPT, acceptor);
            } else {
                doConnectionReg((NioConnection) o);
            }
        }
    }

    private void doConnectionReg(NioConnection connection) {
        boolean connected = connection.channel.isConnected();
        int ops = connected ? 0 : SelectionKey.OP_CONNECT;
        connection.key = register(connection.channel, ops, connection);
        if(connection.key != null) {
            if(connected) {
                connection.finishConnect();
            }
        } else {
            if(connection.regFuture != null) connection.regFuture.done(null);
        }
    }

    private SelectionKey register(SelectableChannel channel, int ops, Object att) {
        try {
            return channel.register(selector, ops, att);
        } catch (ClosedChannelException ioe) {
            logger.error("", ioe);
        }
        return null;
    }

    private void close(SelectableChannel channel) {
        if(channel != null) {
            try {
                channel.close();
            } catch (IOException ioe) {
                //
            }
        }
    }

    //----------------------Acceptor------------------------------
    private class Acceptor implements Processable {

        private ServerSocketChannel serverChannel;

        private Constructor<? extends NioConnection> constructor;
        private Class<? extends IoListener> listenerClass;

        Acceptor(ServerSocketChannel serverChannel, Class<? extends NioConnection> classType, Class<? extends IoListener> listenerClass) throws Exception {
            this.serverChannel = serverChannel;
            this.constructor = classType.getDeclaredConstructor(Reactor.class, SocketChannel.class, IoListener.class);
            this.listenerClass = listenerClass;
        }

        @Override
        public void process(long currentTime, boolean apply) {
            SocketChannel clientChannel;
            int cnt = 0;
            try {
                while(cnt++ < 5) {
                    clientChannel = serverChannel.accept();
                    if (clientChannel != null) {
                        try {
                            clientChannel.socket().setTcpNoDelay(true);
                            clientChannel.configureBlocking(false);
                            NioConnection connection = constructor.newInstance(Reactor.this, clientChannel, listenerClass.newInstance());
                            doConnectionReg(connection);
                        } catch (Exception e) {
                            logger.error("", e);
                            close(clientChannel);
                        }
                    } else {
                        break;
                    }
                }
            } catch (IOException e) {
                if(e.getMessage().equals("open too many files")) {
                    // Prevent the select spin like crazy doing nothing but eating CPU
                    try { Thread.sleep(50); } catch (InterruptedException ignore) { }
                }
                logger.error("", e);
            }
        }
    }

}

