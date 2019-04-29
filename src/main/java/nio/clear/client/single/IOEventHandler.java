package nio.clear.client.single;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public class IOEventHandler {

    private final static Logger logger = LoggerFactory.getLogger(IOEventHandler.class);

    private SocketChannel channel;
    private Selector selector;
    private SelectionKey key;
    private InputOutputListener listener;

    private final static String ReadHeaderState = "head";
    private final static String ReadBodyState = "body";

    private ByteBuffer header = ByteBuffer.allocate(5);
    private ByteBuffer readBuf = header;
    private String readState = ReadHeaderState;

    public final static int PEEK = 1;
    public final static int ACCESS = 1 << 1;
    public final static int FAIL = 1 << 2;

    private Msg writeBuf;
    private int writeState = PEEK;

    private final Object CONSISTENT_LOCK = new Object();
    private final Object KEY_OPS_LOCK = new Object();

    private static final UnfairQueue msgQue = new UnfairQueue();

    public IOEventHandler(InputOutputListener listener, SocketChannel channel, Selector selector, SelectionKey key) {
        this.listener = listener;
        this.channel = channel;
        this.selector = selector;
        this.key = key;
    }

    public void handle() throws Exception {

        if (key.isWritable()) {

            try {
                write();
            } catch (Exception e) {
                //after writing error
                Optional.ofNullable(writeBuf).ifPresent(
                        wb -> listener.onSendFailed(writeBuf.getOrigin(), "send failed!"));
                throw e;
            }

        }

        if (key.isReadable()) {
            Optional.ofNullable(read()).ifPresent(listener::onReceiveMessage);
        }
    }

    private void write() throws Exception {

        if(writeState == PEEK) {
            synchronized (CONSISTENT_LOCK) {    //ensure peek==poll (multi thread)
                writeBuf = msgQue.peek();
                writeState = listener.onCheckBeforeSend(writeBuf); //writeState : PULL PUSH FAIL
                switch (writeState) {
                    case ACCESS: msgQue.poll(); break;
                    case FAIL:
                        msgQue.poll();
                        listener.onSendFailed(writeBuf.getOrigin(), "check failed");
                        writeState = PEEK;break;
                    //case PULL
                }
            }
        }

        if(writeState == ACCESS) {
            ByteBuffer content = writeBuf.getContent();
            channel.write(content);     //IOE
            if (!content.hasRemaining()) {
                listener.onSendSucceed(writeBuf.getOrigin());
                writeState = PEEK;
            }
        }

        if(writeState == PEEK) {
            synchronized (KEY_OPS_LOCK) {
                if (msgQue.isEmpty()) {
                    key.interestOps(SelectionKey.OP_READ);
                }
            }
        }

    }

    private String read() throws Exception {

        int t = channel.read(readBuf);
        if (t == -1) throw new IOException("-1");

        String content = null;

        if(!readBuf.hasRemaining()) {

            content = new String(readBuf.array());

            if(ReadHeaderState.equals(readState)) {
                int bLen = Integer.parseInt(content);   //NumberFormatException
                readBuf = ByteBuffer.allocate(bLen);
                readState = ReadBodyState;
                header.clear();
                return read();  //read a completed msg as more as possible in one time
            }
            else {//ReadBodyState
                readBuf = header;
                readState = ReadHeaderState;
            }
        }
        return content;
    }

    public static boolean hasEmptyQue() {
        return msgQue.isEmpty();
    }

    public boolean addMsg(Msg node) {
        return addMsg(node, -1);
    }

    public boolean addMsgAsFirst(Msg node) {

        synchronized(CONSISTENT_LOCK) {
            return addMsg(node, 0);
        }
    }

    private boolean addMsg(Msg node, int index) {

        //synchronized (KEY_OPS_LOCK) {
        //    if (msgQue.isEmpty()) {
        //        key.interestOps(SelectionKey.OP_READ);
        //    }
        //}
        //锁的作用：
        //1、在msgQue.isEmpty判断为true后，有消息进队列，该消息不能被发送
        //先更新OPS还是先进队？
        //先更新OPS需要在出队时判断空指针，因为此时可能还未进队 即ops=write -> dequeue
        //先更新OPS需要将更新OPS部分上锁，因为可能发生进队前（已更新OPS=WRITE）被更新OPS=READ 即ops=write -> ops=read -> enqueue
        //先进队需要将更新OPS部分上锁，因为可能发生进队后未尝试更新OPS=WRITE却被出队且更新OPS=READ 即enqueue -> ops=read -> ops=write
        synchronized (KEY_OPS_LOCK) {
            try {
                if ((key.interestOps() & SelectionKey.OP_WRITE) == 0) {
                    key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    selector.wakeup();
                }
            } catch (CancelledKeyException cke) {
                logger.error("change key ops failed, channel may be closed", cke);
                return false;
            }
            if(index < 0)
                msgQue.enqueue(node);
            else
                msgQue.enqueue(node, index);
        }

        return true;
    }
}
