package nio.clear.client.single;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public abstract class AbstractAsyncClient implements InputOutputListener {

    private Logger logger = LoggerFactory.getLogger(AbstractAsyncClient.class);

    private String host;
    private int port;

    private SocketChannel channel;
    private Selector selector;

    private volatile boolean isConnected = false;

    private volatile boolean reconnect = true;

    private final Object CONNECT_LOCK = new Object();
    private final Object SELECT_LOCK = new Object();

    private Thread own;
    private IOEventHandler ioHandler;

    AbstractAsyncClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    private void doSelect() throws Exception {

        if(!isConnected) {
            synchronized (SELECT_LOCK) {
                while (!isConnected)     //wait for another thread reconnect until success if lost connection
                    SELECT_LOCK.wait();
            }
        }
        if (selector.select() == 0 && !Thread.currentThread().isInterrupted()) doSelect();
    }

    public void start() {
        own = new Thread(() -> {
            if (connect()) {
                try {
                    while (!Thread.interrupted()) {

                        doSelect();

                        try {
                            ioHandler.handle();
                        } catch (Exception e) {
                            onIOException(e);
                            closeChannel(); //cause isConnected=false
                            connect();
                        }
                        selector.selectedKeys().clear();
                    }
                } catch(Exception e) {
                    logger.error("", e);
                } finally {
                    closeAll();
                }
            }
        });
        own.start();
    }

    public void stop() {
        doBeforeStop();
        reconnect = false;
        if(own != null)
            own.interrupt();
        logger.info("async client stopped");
    }

    //why not onCheck here?
    //1、登陆消息进队，此时断线，清除登陆记录，但登陆消息并未发出，会有多条登陆消息进队
    //2、登陆消息进队，记录登陆记录，另外线程发送消息，检查登陆记录，可能比登陆消息早进队列（可加锁解决）
    //不清除登陆记录？！！！
    public boolean asyncSend(Msg msg) {
        if(connect()) {
            return ioHandler.addMsg(msg);
        }
        return false;
    }

    public boolean asyncSendFirst(Msg msg) {
        if(connect())
            return ioHandler.addMsgAsFirst(msg);
        return false;
    }

    private boolean  connect() {

        synchronized (CONNECT_LOCK) {

            if(isConnected) return true;
            if(!openSelector() || !reconnect) return false;

            logger.debug("connecting~");
            try {//open channel
                channel = SocketChannel.open();
                channel.connect(new InetSocketAddress(host, port));
                channel.socket().setTcpNoDelay(true);
                channel.configureBlocking(false);   //IOException
                //open channel end

                int ops = SelectionKey.OP_READ;
                if (!IOEventHandler.hasEmptyQue()) {
                    ops = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
                }

                SelectionKey key = channel.register(selector, ops);//throws ClosedChannelException
                ioHandler = new IOEventHandler(this, channel, selector, key);

                isConnected = true;

                synchronized (SELECT_LOCK) {
                    SELECT_LOCK.notify();
                }

            } catch (IOException ioe) {
                logger.error("channel init or register failed", ioe);
                closeChannel();
            }
        }
        return isConnected;
    }

    private void closeAll() {
        //synchronized (CONNECT_LOCK) {
            closeChannel();
            closeSelector();
        //}
    }

    private void closeChannel() {
        synchronized (CONNECT_LOCK) {
            if (channel != null && channel.isOpen()) {
                try {
                    channel.close();
                } catch (IOException ioe) {
                    logger.error("channel close error", ioe);
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
            logger.error("selector open fail", ioe);
            return false;
        }
        return true;
    }

    private void closeSelector() {
        if(selector != null && selector.isOpen()) {
            try {
                selector.close();
            } catch (IOException ioe) {
                logger.error("selector close error", ioe);
            }
        }
        selector = null;
    }

    public abstract int onCheckBeforeSend(Msg msg) throws Exception;
    public abstract void doBeforeStop();
    public abstract void onReceiveMessage(String msg);
    public abstract void onIOException(Exception e);
    public abstract void onSendFailed(String writeFailedMsg, String reason);
    public abstract void onSendSucceed(String msg);
}
