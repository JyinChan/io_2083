package com.couger.tradingcenter.server.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class ReadFuture {

    private final static Logger logger = LoggerFactory.getLogger(ReadFuture.class);

    private final Object lock = new Object();

    private volatile Boolean result;

    private ByteBuffer readBuf;
    private String target;

    ReadFuture(String target) {
        if(target == null) {
            logger.debug("can not read a null msg");
            result = false;
        }
        else {
            readBuf = ByteBuffer.allocate(target.length());
            this.target = target;
        }
    }

    ByteBuffer getBuf() {
        return readBuf;
    }

    String done(boolean finished) {
        String readMsg = null;
        if(finished) {
            readMsg = new String(readBuf.array());
            result = readMsg.equals(target);
        } else {
            result = false;
        }
        synchronized (lock) {
            lock.notifyAll();
        }
        return readMsg;
    }

    /**
     *
     * @return true if we read a msg as we expect
     */
    public boolean get() {
        return get(0);
    }

    public boolean get(long timeout) {
        synchronized (lock) {
            if (result != null) return result;
            try {
                lock.wait(timeout);
            } catch (InterruptedException ie) {
                //
            }
            return result;
        }
    }
}
