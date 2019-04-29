package nio.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
//TODO 可见性问题
public abstract class AbstractNioClient extends Thread {

    private static Logger logger = LoggerFactory.getLogger(AbstractNioClient.class);

    private String host;
    private int port;

    private volatile SocketChannel channel;
    private Selector selector;
    private SelectionKey key;
    private final Queue<ByteBuffer> queue;
    private volatile boolean isConnected = false;

    private volatile boolean reconnect = true;

    private final Object CONNECT_LOCK = new Object();
    private final Object SELECT_LOCK = new Object();

    public AbstractNioClient(String host, int port) {
        this.host = host;
        this.port = port;
        queue = new ConcurrentLinkedQueue<>();
    }

    private void doSelect() throws Exception {

        synchronized (SELECT_LOCK) {
            while(!isConnected) {
                SELECT_LOCK.wait();
            }
        }
        int t = selector.select();
        if (t == 0 && !Thread.currentThread().isInterrupted()) doSelect();

    }

    @Override
    public void run() {
        if(connect()) {
            try {
                while (!Thread.interrupted()) {

                    doSelect();

                    //logger.debug("handle start");

                    IOEventHandler handler = (IOEventHandler) key.attachment();

                    try {
                        handler.handle();
                    } catch (Exception e) {
                        logger.error("", e);
                        closeChannel(); //cause isConnected=false
                        connect();
                    }
                    selector.selectedKeys().clear();
                }
            } catch (Exception e) {
                logger.error("", e);
            } finally {
                closeAll();
            }
        } else {
            logger.debug("[{}} connect failed", Thread.currentThread().getName());
        }
    }

    private class IOEventHandler {

        private final static String ReadHeaderState = "head";
        private final static String ReadBodyState = "body";
        private ByteBuffer header = ByteBuffer.allocate(5);
        private ByteBuffer currentBuf = header;
        private String currentState = ReadHeaderState;

        private IOEventHandler() { }

        void handle() throws Exception {

            if (key.isWritable()) {

                try {
                    write();
                } catch (IOException ioe) {
                    //after writing error
                    Optional.ofNullable(queue.poll()).ifPresent(buffer -> {
                        String failedMsg = new String(buffer.array());
                        processWriteFailed(failedMsg);
                    });
                    throw ioe;
                }

            }

            if (key.isReadable()) {
                Optional.ofNullable(read()).ifPresent(AbstractNioClient.this::processMsg);
            }
        }

        private void write() throws IOException {

            ByteBuffer src = queue.peek();
            if(src != null) {   //always not null?
                channel.write(src);
                if (!src.hasRemaining()) {
                    queue.poll();
                    synchronized (queue) {
                        if (queue.isEmpty()) {
                            key.interestOps(SelectionKey.OP_READ);
                        }
                    }
                }
            }
        }

        private String read() throws Exception {

            int t = channel.read(currentBuf);
            if (t == -1) throw new IOException();

            String msg = null;

            if(!currentBuf.hasRemaining()) {

                msg = new String(currentBuf.array());

                if(ReadHeaderState.equals(currentState)) {
                    int bLen = Integer.parseInt(msg);
                    currentBuf = ByteBuffer.allocate(bLen);
                    currentState = ReadBodyState;
                    header.clear();
                    return read();
                }
                else {//ReadBodyState
                    currentBuf = header;
                    currentState = ReadHeaderState;
                }
            }
            return msg;
        }
    }

    public boolean write(String content) {
        if(connect())
            return push(content);
        return false;

    }

    public void setReconnect(boolean reconnect) {
        this.reconnect = reconnect;
    }

    private boolean push(String msg) {

        byte[] b = msg.getBytes();
        msg = String.format("%05d%s", b.length, msg);
        ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes());
        while (buffer.hasRemaining()) {
            try {
                channel.write(buffer);
            } catch (IOException ioe) {

            }
        }

//        synchronized (queue) {
//            try {
//                if((key.interestOps() & SelectionKey.OP_WRITE) == 0) {
//                    key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
//                    selector.wakeup();
//                }
//            } catch (CancelledKeyException cke) {
//                //logger.error("channel may be closed ", cke);
//                return false;
//            }
//            queue.applyRegister(buffer);
//        }

        return true;
    }

    abstract void processMsg(String msg);

    abstract void processWriteFailed(String writeFailedMsg);

    private boolean connect() {
        synchronized (CONNECT_LOCK) {
            if(isConnected) return true;
            if(!openSelector() || !reconnect) return false;

            logger.debug("connecting~");
            try {//open channel
                channel = SocketChannel.open();
                channel.connect(new InetSocketAddress(host, port));
                //channel.socket().setTcpNoDelay(true);
                channel.configureBlocking(false);   //IOException

                //open channel end

                int ops = SelectionKey.OP_READ;
                if (!queue.isEmpty()) {
                    ops = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
                }

                logger.debug("channel{} register", channel.getLocalAddress());
                key = channel.register(selector, ops, new IOEventHandler());//throws ClosedChannelException
                logger.debug("channel{} register success", channel.getLocalAddress());
                isConnected = true;

                synchronized (SELECT_LOCK) {
                    SELECT_LOCK.notify();
                }
            } catch (IOException ioe) {
                logger.error("", ioe);
                closeChannel();
            }
        }
        return isConnected;
    }

    private void closeAll() {
        //synchronized (CONNECT_LOCK) {
            setReconnect(false);
            closeChannel();
            closeSelector();
        //}
    }

    private void closeChannel() {
        synchronized (CONNECT_LOCK) {
            if (channel != null && channel.isOpen()) {
                try {
                    logger.debug("[{}] closed", channel.getLocalAddress());
                    channel.close();
                } catch (IOException ioe) {
                    logger.error("", ioe);
                }
            }
            //channel = null;
            isConnected = false;
        }
    }

    private boolean openSelector() {
        try {
            if(selector == null)
                selector = Selector.open();
        } catch (IOException ioe) {
            //logger.error("selector open failed ", ioe);
            return false;
        }
        return true;
    }

    private void closeSelector() {
        if(selector != null && selector.isOpen()) {
            try {
                selector.close();
            } catch (IOException ioe) {
                //logger.error("selector close error ", ioe);
            }
        }
        selector = null;
    }
}
