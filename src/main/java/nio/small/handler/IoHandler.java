package nio.small.handler;

import nio.small.core.IoConnection;
import nio.small.msg.WriteMsg;

public interface IoHandler {

//    messageReceived
//            messageSent
//    messageSendBefore
//            exceptionCaught
//    sessionClosed
//            sessionCreated
//    readTimeout
//            writeTimeout
//    messagesSendFailed

    void connectionOpened(IoConnection connection);

    void connectionClosed();

    void messageReceived(IoConnection connection, Object message);

    void messageSendBefore(IoConnection connection, WriteMsg msg);

    void messageSent(IoConnection connection, WriteMsg msg);

    void messageSendFailed(IoConnection connection, WriteMsg msg);

    void exceptionCaught(IoConnection connection, Throwable t);

    void handleIdleTimeout(IoConnection connection);
}
