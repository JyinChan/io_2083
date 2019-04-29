package nio.clear.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class NioConnection implements Processable {

    protected final static Logger logger = LoggerFactory.getLogger(NioConnection.class);

    public final static int DEFAULT_IDLE_STATUS = 0;
    public final static int READ_IDLE_STATUS =  0b0001;
    public final static int WRITE_IDLE_STATUS = 0b0010;
    public final static int BOTH_IDLE_STATUS =  0b0100;

    private final static int WAIT_STATUS      = 0b1000;

    public final static int IDLE_TIMEOUT = 11;
    public final static int PING_TIMEOUT = 22;

    private static int autoIncrementId = 0;

    private long lastReadTime = System.currentTimeMillis();

    private long lastWriteTime = lastReadTime;

    private long lastIdleTime;

    private int idleStatus = DEFAULT_IDLE_STATUS;

    private int idleInterval;
    private int idleIntervalBak;

    private boolean isPingActive;
    private String pingRequest;
    private String pingResponse;
    private int pingTimeout;

    private AtomicBoolean config = new AtomicBoolean(false);

    protected SocketChannel channel;
    protected volatile IoListener ioListener;
    protected SelectionKey key;

    protected volatile RegFuture regFuture;

    private int connectionId;

    NioConnection() {}

    protected void finishConnect() {
        if(!channel.isConnected()) {
            try {
                channel.finishConnect();
            } catch (IOException ioe) {
                logger.error("{} socket[{}]", ioe.getMessage(), getRemote());
                try { channel.close(); } catch (IOException ignore) {}
                if (regFuture != null) regFuture.done(null);
                return;
            }
        }
        int ops = 0;
        if(this instanceof GeneralNioConnection)
            ops = SelectionKey.OP_READ;
        key.interestOps(ops);

        connectionId = ++autoIncrementId;

        if (regFuture != null) regFuture.done(this);
        ioListener.connectionOpened(this);
    }

    public void setIoListener(IoListener listener) {
        this.ioListener = listener;
    }

    public void config(int interestedIdleStatus, int idleIntervalMillis, String pingRequest, String pingResponse, boolean isPingActive, int pingTimeoutMillis) {
        if(!config.compareAndSet(false, true))
            throw new IllegalStateException();
        //check simply
        if(interestedIdleStatus != READ_IDLE_STATUS &&
                interestedIdleStatus != WRITE_IDLE_STATUS &&
                interestedIdleStatus != BOTH_IDLE_STATUS &&
                interestedIdleStatus != DEFAULT_IDLE_STATUS) {
            throw new IllegalArgumentException();
        }
        this.idleStatus = interestedIdleStatus;
        this.idleInterval = idleIntervalMillis<1000 ? 1000:idleIntervalMillis;
        this.pingRequest = pingRequest;
        this.pingResponse = pingResponse;
        if(isPingActive && pingTimeoutMillis < 1000)
            throw new IllegalArgumentException();
        this.isPingActive = isPingActive;
        this.pingTimeout = pingTimeoutMillis;
    }

    protected void updateLastReadTime(long time) {
        lastReadTime = time;
    }

    protected void updateLastWriteTime(long time) {
        lastWriteTime = time;
    }

    protected String filter(String originMsg, String decodeMsg) {
        if(logger.isDebugEnabled()) logger.debug("origin[{}] decode[{}]", originMsg, decodeMsg);
        if (pingResponse != null && pingResponse.equals(originMsg)) {
            reset();
            return null;
        }
        else if (pingRequest != null && pingRequest.equals(originMsg)) {
            write(pingResponse);
            return null;
        }
        return decodeMsg;
    }

    protected void notifyIdle(long currentTime) {
        int status = idleStatus;
        switch(status) {
            case DEFAULT_IDLE_STATUS: break;

            case READ_IDLE_STATUS: notifyIdle0(currentTime, lastReadTime);
            break;

            case WRITE_IDLE_STATUS: notifyIdle0(currentTime, lastWriteTime);
            break;

            case BOTH_IDLE_STATUS: notifyIdle0(currentTime, Math.max(lastWriteTime, lastReadTime)); break;

            default: notifyIdle0(currentTime, lastReadTime);
        }
    }

    private void notifyIdle0(long currentTime, long lastIoTime) {
        if(lastIoTime < lastIdleTime) lastIoTime = lastIdleTime;

        if(currentTime - lastIoTime > idleInterval) {
            if ((idleStatus & WAIT_STATUS) == 0) {
                lastIdleTime = currentTime;
                if(isPingActive) {
                    writeAndRead(pingRequest, pingResponse);
                    mark();
                }
                ioListener.onTimeout(this, IDLE_TIMEOUT);
            }
            //ping timeout
            //without update lastIdleTime, so that ping against when next notify
            else {
                reset();
                ioListener.onTimeout(this, PING_TIMEOUT);
            }
        }
    }

    private void mark() {
        idleIntervalBak = idleInterval;
        idleInterval = pingTimeout;
        idleStatus = idleStatus | WAIT_STATUS;
    }

    private void reset() {
        idleInterval = idleIntervalBak;
        idleStatus = idleStatus & ~WAIT_STATUS;
    }

    abstract boolean setApplyMark(boolean v);
    public abstract <K> K write(String writeMsg);
    public <T> T writeAndRead(String writeMsg, String readTarget) {
        write(writeMsg);
        return null;
    }
    public abstract void close();
    public abstract boolean isClosed();

    private String remoteAddress;
    private String localAddress;

    public String getRemote() {
        if(remoteAddress == null) {
            remoteAddress = channel.socket().getInetAddress().toString() + ":" + channel.socket().getPort();
        }
        return remoteAddress;
    }

    public String getLocal() {
        if(localAddress == null) {
            localAddress = channel.socket().getLocalAddress().toString() + ":" + channel.socket().getLocalPort();
        }
        return localAddress;
    }

    @Override
    public String toString() {
        return "conn_" + connectionId;
    }
}
