package nio.begin;

import nio.begin.handler.MessageHandler;
import nio.begin.reader.ChannelReader;
import nio.begin.handler.DefaultServerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NioServer {

    private Logger logger = LoggerFactory.getLogger(NioServer.class);

    private int openProcessor = 1;
    private ServerSocketChannel serverChannel;
    private Class handler ;

    private boolean start = false;
    private boolean build = false;

    public NioServer() {}

    public synchronized NioServer build(String host, int port) {

        logger.info("build server start");

        if(build)
            throw new UnsupportedOperationException("unsupported operation of rebind");
        build = true;

        try {
            serverChannel = ServerSocketChannel.open();
            ServerSocket socket = serverChannel.socket();
            socket.bind(new InetSocketAddress(host, port));
            serverChannel.configureBlocking(false);
        } catch (Exception e) {
            logger.error("build failed", e);
            closeServerSocket();
            build = false;
        }
        return this;
    }

    private void closeServerSocket() {
        if(serverChannel != null && serverChannel.isOpen()) {
            try {
                serverChannel.close();
            } catch (IOException ioe) {
                logger.error("close server oio failed [{}]", ioe);
            }
        }
    }

    public NioServer openProcessor(int num) {
        if(start)
            throw new UnsupportedOperationException("server is running, unsupported operation of changing selector num");
        if(num < 1)
            throw new IllegalArgumentException();
        this.openProcessor = num;
        return this;
    }

    public NioServer handler(Class handler) {
        if(start)
            throw new UnsupportedOperationException("server is running, unsupported operation of changing handler");
        this.handler = handler;
        return this;
    }

    public synchronized void start() {
        if(!build)
            throw new UnsupportedOperationException("unsupported operation of start, must build before");
        if(handler == null)
            handler = DefaultServerHandler.class;

        logger.info("start server");

        if(!start) {

            ExecutorService service = Executors.newFixedThreadPool(openProcessor);

            try {
                selectors = new Selector[openProcessor];
                for (int i = 0; i < openProcessor; i++)
                    selectors[i] = Selector.open();

                SelectionKey serverKey = serverChannel.register(selectors[0], SelectionKey.OP_ACCEPT);
                serverKey.attach(new Acceptor(serverChannel));

                processors = new Processor[openProcessor];
                for (int i = 0; i < openProcessor; i++) {
                    processors[i] = new Processor(selectors[i]);
                    service.execute(processors[i]);
                }
            } catch (Exception e) {
                logger.error("start failed", e);
                System.exit(0);
            }
        }
        logger.info("start server success");
        start = true;
    }

    private Processor processors[];
    private Selector[] selectors;

    private static void invoke(SelectionKey key) {
        Runnable r = (Runnable)key.attachment();
        if(r != null) {
            r.run();
        }
    }

    private class Processor implements Runnable {

        private final Selector selector;

        private final Object REG_LOCK = new Object();

        Processor(Selector selector) {
            this.selector = selector;
        }

        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {

                    synchronized (REG_LOCK) {}  //for register

                    int readyChannels = selector.select();
                    if (readyChannels == 0) continue;

                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIt = selectedKeys.iterator();

                    while (keyIt.hasNext()) {

                        invoke(keyIt.next());
                        keyIt.remove();
                    }

                }
            } catch (Exception e) {
                logger.error("", e);
            }
        }
    }

    private class Acceptor implements Runnable {

        private ServerSocketChannel serverChannel;
        private int next;

        Acceptor(ServerSocketChannel serverChannel) {
            this.serverChannel = serverChannel;
            next = openProcessor ==1 ? 0 : 1;
        }

        public void run() {
            SocketChannel clientChannel;
            try {
                clientChannel = serverChannel.accept();
                if(clientChannel != null) {
                    new Connection(clientChannel, next);
                    if(++next == openProcessor) next = openProcessor ==1 ? 0 : 1;
                }
            } catch(Exception e) {
                logger.error("accept or register channel error {}", e);
            }
        }
    }

    private class Connection implements Runnable {

        private SocketChannel channel;
        private final Selector selector;
        private SelectionKey key;
        private ChannelReader reader;
        private ChannelWriter writer;
        private MessageHandler msgHandler;

        private Connection(SocketChannel channel, int idx) throws IOException {
            this.selector = selectors[idx];
            this.channel = channel;
            this.channel.configureBlocking(false);

            synchronized (processors[idx].REG_LOCK) {
                selector.wakeup();
                key = channel.register(selector, SelectionKey.OP_READ, this);
            }

            reader = new ChannelReader(channel);
            writer = new ChannelWriter(key);
            try {
                msgHandler = (MessageHandler) handler.newInstance();
            } catch (IllegalAccessException | InstantiationException e) {
                logger.debug("instance handler error {}", e);
            }

            logger.info("accept a connection from {}, register to selector{}", channel.getRemoteAddress(), idx);
        }

        @Override
        public void run() {

            try {
                if (key.isReadable()) {
                    Optional.ofNullable(reader.read()).ifPresent(msg -> msgHandler.handle(msg, writer));
                }

                if (key.isWritable()) {
                    writer.write();
                }
            } catch (Exception e) {
                logger.error("handle event error");
                logger.error("this channel will be closed");
                logger.error("{} message can not write to send buffer", writer.getMsgQueue().size());

                msgHandler.handleException(e);

                try {
                    if(channel.isOpen())
                        channel.close();
                } catch (IOException ioe) {
                    logger.error("channel close failed");
                }
            }
        }
    }

}
