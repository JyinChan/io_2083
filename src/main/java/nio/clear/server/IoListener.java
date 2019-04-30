package com.couger.tradingcenter.server.nio;

public interface IoListener {

    void connectionOpened(NioConnection connection);

    void connectionClosed(NioConnection connection);

    void messageReceived(NioConnection connection, String msg);

    void onTimeout(NioConnection connection, int timeoutType);

    void exceptionCaught(NioConnection connection, Exception e);
}
