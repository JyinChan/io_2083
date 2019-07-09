package nio.clear.server;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class GeneralNioConnection extends NioConnection {

    private Reactor reactor;

    private final Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();

    private volatile boolean flushable = false;
    private volatile boolean suspendRead = false;
    private volatile boolean closed = false;
    private volatile boolean unregistered = false;

    private AtomicInteger applyMark = new AtomicInteger(0);

    GeneralNioConnection(Reactor reactor, SocketChannel channel, IoListener ioListener) {
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
                    if (key.isReadable() && !suspendRead) {
                        read(currentTime);
                    }
                    else if (key.isConnectable()){
                        finishConnect();
                    }
                } else {
                    if(flushable) {
                        flushable = false;
                        flush(currentTime);
                    }
                    //if apply suspend
                    suspendNow();

                    if (closed) {
                        closeNow();
                    }
                }
            }

        } catch (CancelledKeyException cke) {
            //only caused by unregister()
            exceptionCaught(cke);
        } catch (Exception e) {
            exceptionCaught(e);
            //must close
            close();
        }
    }

    private static final int ReadHeaderState = 0;
    private static final int ReadBodyState = 1;
    private final ByteBuffer header = ByteBuffer.allocate(5);
    private int readState = ReadHeaderState;
    private ByteBuffer readBuf = header;

    private void read(long currentTime) throws IOException {

        channelRead(readBuf, currentTime);

        if(!readBuf.hasRemaining()) {
            String content = new String(readBuf.array());
            if(readState == ReadHeaderState) {
                readState = ReadBodyState;
                readBuf = ByteBuffer.allocate(Integer.parseInt(content));
                read(currentTime);
            } else {
                readState = ReadHeaderState;
                readBuf = (ByteBuffer) header.clear();
                //todo
                String readMsg = filter(""/*Utility.format05D(content)*/, content);
                if(readMsg != null)
                    try { ioListener.messageReceived(this, readMsg); } catch (Exception e) { exceptionCaught(e); }
            }
        }
    }

    private void flush(long currentTime) throws IOException {
        ByteBuffer writeBuf;
        for(int i = 0; (writeBuf = writeQueue.peek())!=null && i<3; i++) {
            channelWrite(writeBuf, currentTime);
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

    protected boolean setApplyMark(boolean newValue) {
        if(newValue) {
            return applyMark.compareAndSet(0, 1);
        }
        applyMark.set(0);
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
        flushable = writeQueue.add(ByteBuffer.wrap(writeMsg.getBytes()));
        if(flushable) {
            reactor.applyProcess(this);
        }
        return flushable;
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
            try { channel.close(); } catch (IOException ioe) { exceptionCaught(ioe);}
            try { ioListener.connectionClosed(this); } catch (Exception e) { exceptionCaught(e);}
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
