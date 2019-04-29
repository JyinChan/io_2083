package nio.clear.client.multi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NioConnection implements AsyncSender, ChannelReader {

    private final static Logger logger = LoggerFactory.getLogger(NioConnection.class);

    private String host;
    private int port;

    private SocketChannel channel;
    private Selector selector;
    private SelectionKey key;
    private IOListener listener;

    private WriteMsg writeBuf;

    private final Object KEY_OPS_LOCK = new Object();

    private final Queue<WriteMsg> writeMsgQue = new ConcurrentLinkedQueue<>();

    private String probe;
    private int probeInterval;
    private int inactiveTimeLimit;
    private long lastActiveTime;
    private long lastCheckTime;

    //connect state
    private volatile int connectedState = 0;
    private boolean enabled = true;

    private final static int DISCONNECT = 0;
    private final static int CONNECTING = 1;
    private final static int CONNECTED = 2;

    //const param of notify method
    private final static int CLOSED = 1;
    private final static int SEND_SUCC = 3;
    private final static int SEND_FAIL = 4;
    private final static int READABLE = 5;

    public NioConnection(String host, int port, Selector selector, IOListener listener, String probe, int probeInterval) {
        this.host = host;
        this.port = port;
        this.selector = selector;
        this.listener = listener;
        this.probe = probe;
        this.probeInterval = probeInterval;
        inactiveTimeLimit = probeInterval * 3 - 200;
    }

    //here only invoked by select thread
    //otherwise, maybe cause blocking in register
    synchronized void asyncConnect() {
        if(enabled && connectedState == DISCONNECT) {

            try {

                channel = SocketChannel.open();
                channel.configureBlocking(false);
                channel.socket().setTcpNoDelay(true);

                key = channel.register(selector, SelectionKey.OP_CONNECT, this);

                boolean isConnected = channel.connect(new InetSocketAddress(host, port));
                if(isConnected) {
                    logger.debug("connection established immediately");
                    finishConnect();
                } else {
                    connectedState = CONNECTING;
                }

            } catch (Exception e) {
                logger.error("host["+host+":"+port+"]", e);
                closeChannel();
            }
        }
    }

    //invoked when connection established
    private void finishConnect() {
        connectedState = CONNECTED;
        key.interestOps(SelectionKey.OP_READ);
        updateLastActiveTime();
        notify(CONNECTED, this);
    }

    //maybe invoked by non-select thread, but always close directly
    synchronized void disconnect(IOException e) {
        if(connectedState != DISCONNECT) {
            connectedState = DISCONNECT;
            closeChannel();
            notify(CLOSED, e);
        }
    }

    synchronized void disabled() {
        this.enabled = false;
    }

    private boolean isConnected() {
        return connectedState == CONNECTED;
    }

    void checkIdle(long currentTime) {
        long checkInterval = currentTime - lastCheckTime;
        if(checkInterval >= probeInterval) {
            lastCheckTime = currentTime;
            if(currentTime - lastActiveTime >= inactiveTimeLimit || !isConnected()) {
                //关闭连接
                IOException ioe = new IOException("idle timeout");
                disconnect(ioe);
                //重新连接
                asyncConnect();
                return;
            }
            //send probe
            WriteMsg hb = new WriteMsg(probe);
            if ((key.interestOps() & SelectionKey.OP_WRITE) == 0) {
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }
            writeMsgQue.add(hb);
        }
    }

    void doIo() {

        try {

            if (key.isWritable()) {
                try {
                    write();
                } catch (IOException ioe) {
                    //after writing error
                    Optional.ofNullable(writeBuf).ifPresent(
                            wb -> notify(SEND_FAIL, wb.getOrigin()));
                    throw ioe;
                }
            }

            if (key.isReadable()) {
                notify(READABLE, this);
            }

            else if(key.isConnectable()) {
                if(channel.finishConnect()) {   //IOE
                    finishConnect();
                }
            }
        } catch (IOException e) {
            disconnect(e);
        }
    }

    private void write() throws IOException {

        if(writeBuf == null) {
            writeBuf = writeMsgQue.poll();
        }

        if(writeBuf != null) {
            ByteBuffer content = writeBuf.getContent();
            channel.write(content);     //IOE
            if (!content.hasRemaining()) {
                notify(SEND_SUCC, writeBuf.getOrigin());
                writeBuf = null;
            }
        }

        if(writeBuf == null) {
            synchronized (KEY_OPS_LOCK) {
                if (writeMsgQue.isEmpty()) {
                    key.interestOps(SelectionKey.OP_READ);
                }
            }
        }
    }

    @Override
    public void read(ByteBuffer dst) {
        try {
            int r = channel.read(dst);
            if (r == -1)
                throw new IOException("-1");
            if(r > 0)
                updateLastActiveTime();
        } catch (IOException ioe) {
            disconnect(ioe);
        }
    }

    //unchecked
    @Override
    public boolean asyncSend(WriteMsg writeMsg) {
        synchronized (KEY_OPS_LOCK) {
            try {
                //TODO key.interestOps maybe block in some run environment???
                //we can do that:
                //1、enqueue
                //2、wakeup select
                //3、select thread flush the queue
                if ((key.interestOps() & SelectionKey.OP_WRITE) == 0) {
                    key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    selector.wakeup();
                }
            } catch (CancelledKeyException cke) {
                logger.error("channel may be closed", cke);
                return false;
            }
            writeMsgQue.add(writeMsg);
        }
        return true;
    }

    private void updateLastActiveTime() { lastActiveTime = System.currentTimeMillis(); }

    private void closeChannel() {
        if(channel != null) {
            try {
                channel.close();
            } catch (IOException ioe) {
                logger.error("", ioe);
            }
            channel = null;
        }
    }

    //unchecked
    private void notify(int event, Object ...params) {
        try {
            switch (event) {
                case READABLE:
                    listener.onReadable((ChannelReader)params[0]);
                    break;
                case SEND_SUCC:
                    listener.onSendSucceed((String) params[0]);
                    break;
                case CONNECTED:
                    listener.onConnected((AsyncSender) params[0]);
                    break;
                case CLOSED:
                    listener.onClosed((IOException) params[0]);
                    break;
                case SEND_FAIL:
                    listener.onSendFailed((String) params[0]);
                    break;
                default:logger.error("can't find listener's method for event[{}]", event);
            }
        } catch (Throwable t) {
            logger.error("", t);
        }
    }
}
