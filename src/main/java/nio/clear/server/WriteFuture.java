package com.couger.tradingcenter.server.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class WriteFuture {

    private final static Logger logger = LoggerFactory.getLogger(WriteFuture.class);

    private final Object lock = new Object();

    private Boolean result;

    private ByteBuffer writeBuf;

    WriteFuture(String writeMsg) {
        if(writeMsg == null) {
            logger.debug("write[null] is not allowed");
            result = false;
        } else {
            writeBuf = ByteBuffer.wrap(writeMsg.getBytes());
        }
    }

    ByteBuffer getBuf() {
        return writeBuf;
    }

    void done(boolean succeed) {
        synchronized (lock) {
            this.result = succeed;
            lock.notifyAll();
        }
    }

    public boolean get() {

        synchronized (lock) {
            if (result != null) return result;
            try {
                lock.wait();
            } catch (InterruptedException ie) {
                //return false;
            }
            return result;
        }
    }

}
