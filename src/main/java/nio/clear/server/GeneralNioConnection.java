package nio.clear.server;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class GeneralNioConnection extends NioConnection {

    private Reactor reactor;

    private final Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();

    private volatile boolean suspendRead = false;
    private volatile boolean closed = false;
    private volatile boolean unregistered = false;

    private AtomicBoolean applyMark = new AtomicBoolean(false);

    GeneralNioConnection(Reactor reactor, SocketChannel channel, IoListener ioListener) {
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
                    if (key.isReadable() && !suspendRead) {
                        read(currentTime);
                    }
                    else if (key.isConnectable()){
                        finishConnect();
                    }
                } else {
                    //flush apply
                    flush(currentTime);
                    //suspend apply
                    suspendNow();
                    //close apply
                    if (closed) {
                        closeNow();
                    }

                }
            }

        } catch (CancelledKeyException cke) {
            ioListener.exceptionCaught(this, cke);
        } catch (Exception e) { //IOException or NumberFormat even more
            ioListener.exceptionCaught(this, e);
            closeNow();
        }
    }

    private static final int ReadHeaderState = 0;
    private static final int ReadBodyState = 1;
    private final ByteBuffer header = ByteBuffer.allocate(5);
    private int readState = ReadHeaderState;
    private ByteBuffer readBuf = header;

    private void read(long currentTime) throws IOException {

        int r = channel.read(readBuf);
        if(r == -1)
            throw new IOException("-1");
        if(r > 0)
            updateLastReadTime(currentTime);

        if(!readBuf.hasRemaining()) {
            String content = new String(readBuf.array());
            if(readState == ReadHeaderState) {
                readState = ReadBodyState;
                readBuf = ByteBuffer.allocate(Integer.parseInt(content));
                read(currentTime);
            } else {
                readState = ReadHeaderState;
                readBuf = (ByteBuffer) header.clear();
                //todo take place of String.format
                String readMsg = filter(String.format("%05d%s", content.getBytes().length, content), content);
                if(readMsg != null)
                    ioListener.messageReceived(this, readMsg);
            }
        }
    }

    private void flush(long currentTime) throws IOException {
        //if(writeQueue.isEmpty()) return;
        ByteBuffer writeBuf;
        for(int i = 0; (writeBuf = writeQueue.peek())!=null && i<3; i++) {
            if (channel.write(writeBuf) > 0)
                updateLastWriteTime(currentTime);
            if(writeBuf.hasRemaining()) break;
            writeQueue.poll();
        }
        if(writeBuf == null) {  //empty queue
            if((key.interestOps() & SelectionKey.OP_WRITE) != 0) {
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            }
        } else if((key.interestOps() & SelectionKey.OP_WRITE) == 0) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
    }

    boolean setApplyMark(boolean newValue) {
        if(newValue) {
            return applyMark.compareAndSet(false, true);
        }
        applyMark.set(false);
        return true;
    }

    //ensure valid immediately unless a read operation is going on!
    public void suspendRead(boolean newValue) {
        if(closed || unregistered) {
            logger.warn("connection is closed or unregistered, suspendRead[{}] failed", newValue);
            return;
        }
        if(newValue != suspendRead) {
            suspendRead = newValue;
            if(reactor.isReactorThread()) {
                suspendNow();
            } else {
                reactor.applyProcess(this);
            }
        }
    }

    private void suspendNow() {
        if (suspendRead) {
            if((key.interestOps() & SelectionKey.OP_READ) != 0) {
                key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
            }
        } else if((key.interestOps() & SelectionKey.OP_READ) == 0){
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Boolean write(String writeMsg) {
        if(closed || unregistered) {
            logger.warn("connection is closed or unregistered, write[{}] failed", writeMsg);
            return false;
        }
        if(writeMsg == null) {
            logger.warn("write[null] is not allowed");
            return false;
        }
        writeQueue.add(ByteBuffer.wrap(writeMsg.getBytes()));
        reactor.applyProcess(this);
        return true;
    }

    @Override
    public void close() {
        if(!closed && !unregistered) {
            unregistered = true;
            closed = true;
            if(reactor.isReactorThread()) {
                closeNow();
            } else {
                reactor.applyProcess(this);
            }
        }
    }

    private void closeNow() {
        if(channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException ioe) {
                ioListener.exceptionCaught(this, ioe);
            }
            ioListener.connectionClosed(this);
            closed = true;
        }
    }

    //unregister from selector but don't close.
    //return this socket in blocking mode.
    //will cause CancelKeyException thrown if write or read is going on
    public Socket unregister() throws IOException {
        //if(write || read is going on)
        //throw new IllegalStateException();
        if(!writeQueue.isEmpty())
            logger.warn("writeQueue is not empty, data in writeQueue maybe lost");
        if(!unregistered) {
            unregistered = true;
            key.cancel();
            channel.configureBlocking(true);
        }
        return channel.socket();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
