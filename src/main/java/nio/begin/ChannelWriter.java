package nio.begin;

import nio.begin.writer.MessageQueue;
import nio.begin.writer.Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class ChannelWriter implements Writer {

    private final static Logger logger = LoggerFactory.getLogger(ChannelWriter.class);

    private final MessageQueue<ByteBuffer> msgQueue = new MessageQueue<>();

    private SelectionKey key;

    public ChannelWriter(SelectionKey key) {
        this.key = key;
    }

    @Override
    public void write(String content) {
        if(content == null)
            throw new NullPointerException();

        ByteBuffer message = Protocol.formatMessage(content);

        synchronized (msgQueue) {
            try {
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            } catch (CancelledKeyException cke) {
                logger.error("change key ops failure, channel may be closed, {}", cke);
                if(key.channel().isOpen()) {
                    try {
                        key.channel().close();
                    } catch (IOException ioe) {
                        logger.error("close channel error {}", ioe);
                    }
                }
            }
            msgQueue.add(message);
        }
        key.selector().wakeup();

    }

    protected void write() throws Exception {
        ByteBuffer src = msgQueue.peek();
        if(src != null) {
            SocketChannel channel = (SocketChannel) key.channel();
            channel.write(src); //IOE
            if (!src.hasRemaining()) {
                msgQueue.poll();
            }
        }
        synchronized (msgQueue) {
            if (msgQueue.isEmpty()) {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    protected MessageQueue<ByteBuffer> getMsgQueue() {
        return msgQueue;
    }

}
