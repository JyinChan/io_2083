package oio.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public abstract class AbstractAsyncClient implements InputOutputListener {

    private final static Logger logger = LoggerFactory.getLogger(AbstractAsyncClient.class);

    private int ip;
    private String host;

    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile int socketVersion;

    private ReaderRunner readerRunner;
    private WriterRunner writerRunner;

    private volatile boolean connected = false;
    private volatile boolean stop = false;

    private final Object CONNECT_LOCK = new Object();

    AbstractAsyncClient(String host, int ip) {
        this.ip = ip;
        this.host = host;
    }

    public void start() {
        if(connect()) {
            readerRunner = new ReaderRunner(socketVersion, inputStream, this);
            readerRunner.start();
            writerRunner = new WriterRunner(socketVersion, outputStream, this);
            writerRunner.start();
            logger.info("async client start succeed");
        } else {
            logger.info("async client start failed");
        }
    }

    public void stop() {
        doBeforeStop();
        stop = true;
        if(readerRunner != null && readerRunner.isAlive())
            readerRunner.interrupt();
        if(writerRunner != null && writerRunner.isAlive())
            writerRunner.interrupt();
        disConnect(socketVersion);
    }

    public boolean asyncSend(Msg msg) {
        if (connect())  //如果长时间断线重连失败，可能产生大量日志
            return writerRunner.asyncSend(msg);
        return false;
    }

    private boolean connect() {
        if(stop) return false;
        synchronized (CONNECT_LOCK) {
            if (!connected) {
                logger.debug("thread[{}] connecting~", Thread.currentThread().getName());
                socket = new Socket();
                try {
                    socket.connect(new InetSocketAddress(host, ip));
                    socket.setTcpNoDelay(true);

                    inputStream = socket.getInputStream();
                    outputStream = socket.getOutputStream();

                    connected = true;
                    socketVersion++;

                    logger.debug("connect succeed, newSocket[{}]", socketVersion);

                    CONNECT_LOCK.notifyAll();

                } catch (Exception e) {
                    logger.error("", e);
                    disConnect(socketVersion);
                }
            }
            return connected;
        }
    }

    private void disConnect(int oldVersion) {

        synchronized (CONNECT_LOCK) {

            if(socketVersion == oldVersion && socket != null) {
                try {
                    socket.close();
                } catch (IOException ioe) {
                    logger.error("", ioe);
                }
                socket = null;
                connected = false;
            }
        }
    }

    @Override
    public void onException(Exception e) {
        logger.error("", e);
    }

    @Override
    public final void onReconnection(IOException ioe, int oldSocket, ReconnectCallback callback) {
        logger.error("thread[{}]", Thread.currentThread().getName());
        logger.error("", ioe);
        synchronized (CONNECT_LOCK) {
            disConnect(oldSocket);
            try {
                while(!connect()) {
                    logger.debug("thread[{}] wait for reconnect~", Thread.currentThread().getName());
                    CONNECT_LOCK.wait();
                }
                if(connect()) {
                    logger.debug("thread[{}] get a newSocket[{}]", Thread.currentThread().getName(), socketVersion);
                    callback.call(socket, socketVersion);
                }
            } catch (Exception e) {
                logger.error("", e);
            }
        }

    }

    public abstract void doBeforeStop();

}
