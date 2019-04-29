package nio.begin;

import nio.begin.handler.CompletionHandler;
import nio.begin.handler.DefaultClientHandler;
import nio.begin.reader.ChannelReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Optional;

public class NioClient {

    private final static Logger logger = LoggerFactory.getLogger(NioClient.class);

    private SocketChannel channel;
    private Selector selector;
    private SelectionKey key;

    private volatile boolean isConnected = false;

    private final Object CONNECT_LOCK = new Object();
    private final Object SELECT_LOCK = new Object();

    private volatile ChannelReader reader;
    private volatile ChannelWriter writer;
    private volatile CompletionHandler clientHandler;

    private String host ;
    private int port;

    private volatile boolean start = false;

    private Class handler;

    private Thread runner;

    public NioClient() {}

    private NioClient connect() {
        return connect(host, port);
    }

    public NioClient connect(String host, int port) {

        synchronized (CONNECT_LOCK) {

            if(isConnected || !openSelector()) return this;

            this.host = host;
            this.port = port;

            try {//open channel
                channel = SocketChannel.open();
                channel.socket().setSoTimeout(60 * 1000);
                channel.connect(new InetSocketAddress(host, port));
                channel.socket().setTcpNoDelay(true);
                channel.configureBlocking(false);   //IOException

                //open channel end

                //register
                int ops = SelectionKey.OP_READ;
                if (writer != null && !writer.getMsgQueue().isEmpty()) {
                    ops = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
                }

                key = channel.register(selector, ops);//throws ClosedChannelException

                synchronized (SELECT_LOCK) {
                    isConnected = true;
                    SELECT_LOCK.notifyAll();
                }

                //init
                reader = new ChannelReader((SocketChannel) key.channel());
                writer = new ChannelWriter(key);
                if (handler == null)
                    handler = DefaultClientHandler.class;

                clientHandler = (CompletionHandler) handler.newInstance();
                //init end
            } catch (Exception e) {
                logger.error("channel init or register failed {}", e);
                closeChannel();
            }
        }

        logger.info("client connect status [{}]", isConnected);
        return this;
    }

    public NioClient handler(Class handler) {
        if(start)
            throw new UnsupportedOperationException();
        if(handler == null) {
            throw new NullPointerException();
        }
        this.handler = handler;
        return this;
    }

    public synchronized NioClient start() {

        if(isConnected) {

            if (!start) {
                start = true;

                runner = new Thread(() -> {
                    try {
                        while (!Thread.interrupted()) {

                            doSelect();

                            try {
                                handle();
                            } catch (Exception e) {
                                if(start) {
                                    clientHandler.handleException(e);
                                    closeChannel();
                                    connect();
                                }
                            }
                            selector.selectedKeys().clear();
                        }
                    } catch (Exception e) {
                        logger.error("", e);
                    } finally {
                        closeAll();
                    }
                    logger.info("stop finish!");
                });
                runner.start();
                logger.info("start client success");
            }
        } else {
            logger.warn("make sure connect before");
        }

        return this;
    }

    private void handle() throws Exception {
        if (key.isWritable()) {
            try {
                writer.write();
            } catch (Exception e) {
                //after writing error
                Optional.ofNullable(writer.getMsgQueue().poll())
                        .ifPresent(buffer -> {
                    String failedMsg = new String(buffer.array());
                    clientHandler.handleWriteFailed(failedMsg);
                });
                throw e;
            }
        }

        if (key.isReadable()) {
            Optional.ofNullable(reader.read())
                    .ifPresent((msg) -> clientHandler.handle(msg, writer));
        }
    }

    private void doSelect() throws Exception {

        if(!isConnected) {
            synchronized (SELECT_LOCK) {
                while (!isConnected)
                    SELECT_LOCK.wait();
            }
        }

        if (selector.select() == 0 && !Thread.currentThread().isInterrupted()) doSelect();
    }

    public boolean write(String content) {
        synchronized (CONNECT_LOCK) {
            if(!isConnected)
                connect().start();
            if(isConnected)
                writer.write(content);
            return isConnected;
        }
    }

    public void stop() {
        if(runner != null) {
            logger.info("stopping program is running");
            runner.interrupt();
            runner = null;
            start = false;
        }
    }

    private void closeAll() {
        synchronized (CONNECT_LOCK) {
            closeChannel();
            closeSelector();
        }
    }

    private void closeChannel() {
        synchronized (CONNECT_LOCK) {
            if (channel != null && channel.isOpen()) {
                try {
                    channel.close();
                    logger.info("channel has bean closed");
                } catch (IOException ioe) {
                    logger.error("channel close error, {}", ioe);
                }
            }
            channel = null;
            isConnected = false;
        }
    }

    private boolean openSelector() {
        try {
            if(selector == null)
                selector = Selector.open();
        } catch (IOException ioe) {
            logger.error("selector open fail, {}", ioe);
            return false;
        }
        return true;
    }

    private void closeSelector() {
        if(selector != null && selector.isOpen()) {
            try {
                selector.close();
            } catch (IOException ioe) {
                logger.error("selector close error, {}", ioe);
            }
        }
        selector = null;
    }
}
