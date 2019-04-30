package com.couger.tradingcenter.server.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SupportAsyncResultNioC extends NioConnection {

    private Reactor reactor;

    private final Queue<ReadFuture> readQueue = new ConcurrentLinkedQueue<>();
    private final Queue<WriteFuture> writeQueue = new ConcurrentLinkedQueue<>();

    private volatile boolean interestRead = false;

    private volatile boolean closed = false;

    private boolean applyMark = false;

    SupportAsyncResultNioC(Reactor reactor, SocketChannel channel, IoListener ioListener) {
        super(channel, ioListener);
        this.reactor = reactor;
    }

    @Override
    public void process(long currentTime, boolean isApply) {
        try {
            if(key.isValid()) {
                if (!isApply) {
                    if (key.isWritable())
                        flush(currentTime);
                    if (key.isReadable()) {
                        read(currentTime);
                    }
                    else if (key.isConnectable()){
                        finishConnect();
                    }
                } else {

                    flush(currentTime);

                    if(interestRead) {
                        interestRead = false;
                        if((key.interestOps() & SelectionKey.OP_READ) == 0) {
                            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                        }
                    }

                    if (closed) {
                        release();
                    }
                }
            }
        } catch (Exception e) {
            exceptionCaught(e);
            close();
        }
    }

    @Override
    protected void finishConnect() {
        key.interestOps(0);
        super.finishConnect();
    }

    private void read(long currentTime) throws IOException {
        ReadFuture readFuture = null;
        ByteBuffer readBuf;
        try {
            for (int i = 0; (readFuture = readQueue.peek()) != null && i < 3; i++) {
                readBuf = readFuture.getBuf();
                channelRead(readBuf, currentTime);  //IOE
                if(readBuf.hasRemaining()) break;
                readQueue.poll();
                String m = readFuture.done(true);
                m = filter(m, m);
                if(m != null)
                    try { ioListener.messageReceived(this, m); } catch (Exception e) { exceptionCaught(e); }
            }
        } catch (IOException ioe) {
            readFuture.done(false);
            throw ioe;
        }

        if(readFuture == null) {    //empty queue
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
        }
    }

    private void flush(long currentTime) throws IOException {
        WriteFuture future = null;
        ByteBuffer writeBuf;
        try {
            for (int i = 0; (future = writeQueue.peek()) != null && i < 3; i++) {
                writeBuf = future.getBuf();
                channelWrite(writeBuf, currentTime);    //IOE
                if (writeBuf.hasRemaining()) break;
                future.done(true);
                writeQueue.poll();
            }
        } catch (IOException ioe) {
            future.done(false);
            throw ioe;
        }
        if (future == null) {  //empty queue
            if ((key.interestOps() & SelectionKey.OP_WRITE) != 0) {
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            }
        } else if ((key.interestOps() & SelectionKey.OP_WRITE) == 0) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
    }

    /**
     * newV eq false always return true
     * newV eq true then return !applyMark;
     * @param newValue new value of applyMark
     * @return true if mark success
     */
    protected boolean setApplyMark(boolean newValue) {
        boolean apply = applyMark;
        applyMark = newValue;
        return !newValue || !apply;
    }

    /**
     * write in buffer and apply flush.
     * please ensure it will response nothing, otherwise, see <code>writeAndRead</code>
     * @param writeMsg the content we want to send
     * @return WriteFuture
     */
    @SuppressWarnings("unchecked")
    @Override
    public WriteFuture write(String writeMsg) {
        WriteFuture future = new WriteFuture(writeMsg);
        synchronized (writeQueue) {
            if(closed) {
                logger.warn("connection is closed, write[{}] failed", writeMsg);
                future.done(false);
                return future;
            }
            writeQueue.add(future);
            reactor.applyProcess(this);
            return future;
        }
    }

    /**
     * please ensure it will response a msg immediately and directly
     * @param writeMsg the content we want to send
     * @param readTarget the content we expect received
     * @return ReadFuture
     */
    @SuppressWarnings("unchecked")
    @Override
    public ReadFuture writeAndRead(String writeMsg, String readTarget) {
        ReadFuture readFuture = new ReadFuture(readTarget);
        if(readTarget == null) {
            return readFuture;
        }
        synchronized (readQueue) {
            if (closed) {
                logger.warn("connection is closed, write[{}] failed", writeMsg);
                readFuture.done(false);
                return readFuture;
            }
            readQueue.add(readFuture);
            WriteFuture writeFuture = new WriteFuture(writeMsg);
            writeQueue.add(writeFuture);
            interestRead = true;
            reactor.applyProcess(this);
            return readFuture;
        }
    }

    @Override
    public void close() {
        if(!closed) {
            closed = true;
            if(reactor.isReactorThread()) {
                release();
            } else {
                reactor.applyProcess(this);
            }
        }
    }

    private void release() {
        if(channel.isOpen()) {
            try { channel.close(); } catch (IOException ioe) { exceptionCaught(ioe);}
            try { ioListener.connectionClosed(this); } catch (Exception e) { exceptionCaught(e);}
        }
        synchronized (writeQueue) {
            synchronized (readQueue) {
                for (; ; ) {
                    ReadFuture readFuture = readQueue.poll();
                    if (readFuture == null) break;
                    readFuture.done(false);
                }
                for (; ; ) {
                    WriteFuture writeFuture = writeQueue.poll();
                    if (writeFuture == null) break;
                    writeFuture.done(false);
                }
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
