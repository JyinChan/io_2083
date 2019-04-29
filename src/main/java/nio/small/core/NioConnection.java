package nio.small.core;

import nio.small.ConnectionInitializer;
import nio.small.buffer.ByteBuf;
import nio.small.handler.IoHandler;
import nio.small.msg.WriteMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class NioConnection extends IoConnection implements Processable, Registrable {

    private final static Logger logger = LoggerFactory.getLogger(NioConnection.class);

    private SelectionKey key;

    private Processor processor;

    private ConnectionConfig config;

    private IoHandler handler;

    private final Queue<WriteMsg> writeQueue = new ConcurrentLinkedQueue<>();

    private List<Object> receivedMessage = new ArrayList<>();
    private ByteBuf readBuf;

    private AtomicBoolean applyMark = new AtomicBoolean(false);
    private volatile boolean suspendRead = false;
    private volatile boolean closed = false;

    private Supplier<Boolean> interestRead;
    private Supplier<Boolean> interestWrite;

    public NioConnection(Processor processor, SocketChannel channel, ConnectionInitializer initializer) {
        this.processor = processor;
        this.channel = channel;
        this.config = new ConnectionConfig(this);
        initializer.initialize(config);
        handler = config.getHandler();
        readBuf = new ByteBuf(config.getReadBufferCapacity());
        interestRead = () -> (key.interestOps() & SelectionKey.OP_READ) != 0;
        interestWrite = () -> (key.interestOps() & SelectionKey.OP_WRITE) != 0;
    }

    @Override
    public void process(long currentTime, boolean isApply) {
        try {
            if(!isApply) {
                if (key.isWritable()) {
                    flush(currentTime);
                }
                if (key.isReadable() && !suspendRead) {
                    read(currentTime);
                    config.getDecoder().decode(readBuf, receivedMessage);
                    //invoke handler
                } else if (key.isConnectable()) {
                    if (channel.finishConnect()) {
                        notify(OPENED);
                    }
                }
            } else {

                flush(currentTime);

                if (suspendRead) {
                    if(interestRead.get())
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                } else {
                    if(!interestRead.get())
                        key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                }
                //close apply
                if(closed) {
                    //try flush against
                    flush(currentTime);
                    closeNow();
                }
            }
        } catch (IOException ioe) {
            closeNow();
            notify(EXCEPTION);
        } catch (Exception e) {
            notify(EXCEPTION);
        }
    }

    private void read(long currentTime) throws IOException {
        readBuf.beginWrite();
        int r = channel.read(readBuf.getBuffer());
        if(r == -1) throw new IOException("-1");
        if(r > 0) updateLastReadTime(currentTime);
        readBuf.finishWrite();
    }

    private void flush(long currentTime) throws IOException {
        WriteMsg writeMsg;
        ByteBuffer buffer = null;
        for(int i = 0; (writeMsg = writeQueue.peek())!=null && i<config.getMaxNumOfWrite(); i++) {
            buffer = writeMsg.getBuffer();
            if (channel.write(buffer) > 0) {
                updateLastWriteTime(currentTime);
            }
            if(buffer.hasRemaining()) break;
            writeQueue.poll();
        }
        if(buffer == null) {  //empty queue
            if(interestWrite.get())
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        } else {
            if(!interestWrite.get())
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
    }

    @Override
    public void register() {
        Selector selector = processor.getSelector();
        try {
            channel.configureBlocking(false);
            key = channel.register(selector, SelectionKey.OP_READ, this);
        } catch (IOException ioe) {
            logger.error("", ioe);
        }

    }

    @Override
    public void suspendRead(boolean newValue) {
        if(closed) {
            logger.warn("connection is closed or unregistered, suspendRead[{}] failed", newValue);
            return;
        }
        if(newValue != suspendRead) {
            suspendRead = newValue;
            processor.applyProcess(this);
        }
    }

    //write in buffer and apply flush.
    @Override
    public void write(WriteMsg writeMsg) {
        if(writeMsg == null) {
            logger.warn("write[null] is not allowed");
            return;
        }
        if(closed) {
            logger.warn("connection is closed or unregistered, write[{}] failed", writeMsg.getOrigin());
            return;
        }
        String encodeMsg = config.getEncoder().encode(writeMsg.getOrigin());
        if(encodeMsg != null) {
            ByteBuffer buffer = ByteBuffer.wrap(encodeMsg.getBytes());
            writeMsg.setBuffer(buffer);
            writeQueue.add(writeMsg);
            processor.applyProcess(this);
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            processor.applyProcess(this);
        }
    }

    private void closeNow() {
        try {
            channel.close();
        } catch (IOException ioe) {
            logger.error("", ioe);
        }
        if(!channel.isOpen()) {
            notify(CLOSED);
        }
    }

    boolean setApplyMark(boolean newValue) {
        if(newValue) {
            return applyMark.compareAndSet(false, true);
        }
        applyMark.set(false);
        return true;
    }

    //unchecked
    @Override
    void notify(int event, Object ...params) {
        try {
            switch (event) {
                case RECEIVED : handler.messageReceived(null, null); break;
                case SENT : handler.messageSent(null, null); break;
                case SEND_BEFORE : handler.messageSendBefore(null, null); break;
                case EXCEPTION : handler.exceptionCaught(null, null); break;
                case OPENED : handler.connectionOpened(null); break;
                case CLOSED : handler.connectionClosed(); break;
                case SEND_FAIL : handler.messageSendFailed(null, null);
                default:logger.error("can't find handler's method for event[{}]", event);
            }
        } catch (Throwable t) {
            logger.error("", t);
        }
    }

}
