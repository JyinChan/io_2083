package nio.clear.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Optional;
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
        this.reactor = reactor;
        this.channel = channel;
        this.ioListener = ioListener;
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
                    //flush apply
                    flush(currentTime);

                    //interest read apply
                    if(interestRead) {
                        interestRead = false;
                        if((key.interestOps() & SelectionKey.OP_READ) == 0) {
                            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                        }
                    }
                    //close apply
                    if (closed) {
                        release();
                    }

                }
            }
        } catch (CancelledKeyException cke) {
            ioListener.exceptionCaught(this, cke);
        } catch (Exception e) { //IOException even more
            ioListener.exceptionCaught(this, e);
            release();
        }
    }

    private void read(long currentTime) throws IOException {
        ReadFuture readFuture = null;
        try {
            for (int i = 0; (readFuture = readQueue.peek()) != null && i < 3; i++) {
                ByteBuffer readBuf = readFuture.getBuf();
                int r = channel.read(readBuf);
                if (r == -1)
                    throw new IOException("-1");
                if (r > 0)
                    updateLastReadTime(currentTime);
                if(readBuf.hasRemaining()) break;
                String readMsg = readFuture.done(true);
                Optional.ofNullable(filter(readMsg, readMsg))
                        .ifPresent(m -> ioListener.messageReceived(this, m));
                readQueue.poll();
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
        try {
            for (int i = 0; (future = writeQueue.peek()) != null && i < 3; i++) {
                if (channel.write(future.getBuf()) > 0)
                    updateLastWriteTime(currentTime);
                if (future.getBuf().hasRemaining()) break;
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

    boolean setApplyMark(boolean newValue) {
        //newV = false => return true
        //newV = true => return !applyMark;
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
        }
        reactor.applyProcess(this);
        return future;
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
        synchronized (writeQueue) {
            synchronized (readQueue) {
                if (channel.isOpen()) {
                    try {
                        channel.close();
                    } catch (IOException ioe) {
                        ioListener.exceptionCaught(this, ioe);
                    }
                    ioListener.connectionClosed(this);
                    closed = true;
                }
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
