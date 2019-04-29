package nio.small.core;

import nio.small.msg.WriteMsg;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public abstract class IoConnection {

    public final static int DEFAULT_IDLE_STATUS =  0;
    public final static int READ_IDLE_STATUS =  0b0001;
    public final static int WRITE_IDLE_STATUS = 0b0010;
    public final static int BOTH_IDLE_STATUS =  0b0100;
    private final static int WAIT_STATUS      = 0b1000;

    final static int CLOSED = 1;
    final static int OPENED = 2;
    final static int SENT = 3;
    final static int SEND_FAIL = 4;
    final static int SEND_BEFORE = 5;
    final static int EXCEPTION = 6;
    final static int RECEIVED = 7;
    final static int IDLE_TIMEOUT = 8;

    private long lastReadTime = System.currentTimeMillis();

    private long lastWriteTime = lastReadTime;

    private long lastIdleTime;

    protected SocketChannel channel;

    private int idleStatus;
    private int idleIntervalBak;
    private int idleInterval = 30;
    private int pingTimeout = 15;

    private String pingRequest;
    private String pingResponse;

    protected void updateLastReadTime(long time) {
        lastReadTime = time;
    }

    protected void updateLastWriteTime(long time) {
        lastWriteTime = time;
    }

    protected String filter(String msg) {
        if (pingResponse != null && pingResponse.equals(msg)) {
            reset();
            return null;
        }
        else if (pingRequest!= null && pingRequest.equals(msg)) {
            write(WriteMsg.create(pingRequest));
            return null;
        }
        return msg;
    }

    protected void notifyIdle(long currentTime) {
        switch(idleStatus) {
            case 0: break;

            case READ_IDLE_STATUS: notifyIdle0(currentTime, lastReadTime);
                break;

            case WRITE_IDLE_STATUS: notifyIdle0(currentTime, lastWriteTime);
                break;

            case BOTH_IDLE_STATUS: notifyIdle0(currentTime, Math.max(lastWriteTime, lastReadTime));
                break;

            default: notifyIdle0(currentTime, lastReadTime);
        }
    }

    private void notifyIdle0(long currentTime, long lastIoTime) {
        //假设因为太久没收到消息引起ping的发送，并且ping无响应，若不取lastIdleTime会引起频繁操作
        if(lastIoTime < lastIdleTime) lastIoTime = lastIdleTime;

        if(currentTime - lastIoTime > idleInterval) {
            if ((idleStatus & WAIT_STATUS) == 0) {
                lastIdleTime = currentTime;
                if(pingResponse != null) {
                    write(WriteMsg.create(pingRequest));
                    mark();
                } else {
                    notify(IDLE_TIMEOUT);
                }
            }
            //ping timeout
            //without update lastIdleTime, so that ping against after 1s
            else {
                reset();
                notify(IDLE_TIMEOUT);
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

    public abstract void suspendRead(boolean suspend);
    public abstract void close();
    public abstract void write(WriteMsg writeMsg);
    abstract void notify(int event, Object ...params);

    private String remoteAddress;
    private String localAddress;

    public String getRemote() {
        if(remoteAddress == null && channel.isOpen()) {
            try {
                remoteAddress = channel.getRemoteAddress().toString();
            } catch (IOException ioe) {
                notify(EXCEPTION, ioe);
            }
        }
        return remoteAddress;
    }

    public String getLocal() {
        if(localAddress == null && channel.isOpen()) {
            try {
                localAddress = channel.getRemoteAddress().toString();
            } catch (IOException ioe) {
                notify(EXCEPTION, ioe);
            }
        }
        return localAddress;
    }

    protected void setIdleStatus(int idleStatus) {
        this.idleStatus = idleStatus;
    }

    protected void setIdleInterval(int idleInterval) {
        this.idleInterval = idleInterval;
    }

    protected void setPingTimeout(int pingTimeout) {
        this.pingTimeout = pingTimeout;
    }

    protected void setPingRequest(String pingRequest) {
        this.pingRequest = pingRequest;
    }

    protected void setPingResponse(String pingResponse) {
        this.pingResponse = pingResponse;
    }
}
