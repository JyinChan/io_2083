package nio.small.core;

import nio.small.coder.DefaultMsgDecoder;
import nio.small.coder.DefaultMsgEncoder;
import nio.small.coder.MsgDecoder;
import nio.small.coder.MsgEncoder;
import nio.small.handler.IoHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionConfig {

    private final static Logger logger = LoggerFactory.getLogger(ConnectionConfig.class);

    private final static MsgEncoder defaultEncoder = new DefaultMsgEncoder();

    private final static MsgDecoder defaultDecoder = new DefaultMsgDecoder();

    private final static IoHandler defaultHandler = null;

    private final static int MaxNumOfWrite = 1;

    private final static int ReadBufferCapacity = 32;

    private MsgEncoder encoder = defaultEncoder;
    private MsgDecoder decoder = defaultDecoder;
    private IoHandler handler = defaultHandler;

    private int maxNumOfWrite = MaxNumOfWrite;    //一次写事件中 最大发送量
    private int readBufferCapacity = ReadBufferCapacity;

    private IoConnection connection;

    public ConnectionConfig(IoConnection connection) {
        this.connection = connection;
    }

    public ConnectionConfig configEncoder(MsgEncoder encoder) {
        if(encoder == null) {
            logger.warn("MsgEncoder is null, a default MsgEncoder will be config");
            return this;
        }
        this.encoder = encoder;
        return this;
    }

    public ConnectionConfig configDecoder(MsgDecoder decoder) {
        if(decoder == null) {
            logger.warn("MsgDecoder is null, default MsgDecoder will be config");
            return this;
        }
        this.decoder = decoder;
        return this;
    }

    public ConnectionConfig configHandler(IoHandler handler) {
        if(handler == null) {
            logger.warn("IoHandler is null, default IoHandler will be config");
            return this;
        }
        this.handler = handler;
        return this;
    }

    public ConnectionConfig configMaxNumOfWrite(int maxNumOfWrite) {
        if(maxNumOfWrite < MaxNumOfWrite) {
            logger.warn("maxNumOfWrite is lt 1, default value will be config");
            return this;
        }
        this.maxNumOfWrite = maxNumOfWrite;
        return this;
    }

    public ConnectionConfig configReadBufferCapacity(int readBufferCapacity) {
        if(readBufferCapacity < ReadBufferCapacity) {
            logger.warn("readBufferCapacity is lt default value, default value will be config");
            return this;
        }
        this.readBufferCapacity = readBufferCapacity;
        return this;
    }

    public ConnectionConfig configIdleInterval(int idleInterval) {
        connection.setIdleInterval(idleInterval);
        return this;
    }

    public ConnectionConfig configPingTimeout(int pingTimeout) {
        connection.setPingTimeout(pingTimeout);
        return this;
    }

    public ConnectionConfig interestedIdleStatus(int interestedIdleStatus) {
        connection.setIdleStatus(interestedIdleStatus);
        return this;
    }

    public ConnectionConfig configPingRequest(String pingRequest) {
        connection.setPingRequest(pingRequest);
        return this;
    }

    public ConnectionConfig configPingResponse(String pingResponse) {
        connection.setPingResponse(pingResponse);
        return this;
    }

    //getter
    public MsgEncoder getEncoder() {
        return encoder;
    }

    public MsgDecoder getDecoder() {
        return decoder;
    }

    public IoHandler getHandler() {
        return handler;
    }

    public int getMaxNumOfWrite() {
        return maxNumOfWrite;
    }

    public int getReadBufferCapacity() {
        return readBufferCapacity;
    }
}
