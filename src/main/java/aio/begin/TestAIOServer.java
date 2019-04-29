package aio.begin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class AcceptCompletionHandler0 implements CompletionHandler<AsynchronousSocketChannel, Void> {

    AsynchronousServerSocketChannel asyServer;

    public AcceptCompletionHandler0(AsynchronousServerSocketChannel asyServer) {
        this.asyServer = asyServer;
    }
    @Override
    public void completed(AsynchronousSocketChannel result, Void attachment) {
        asyServer.accept(null, this);
        ByteBuffer buf = ByteBuffer.allocate(5);
        result.read(buf, buf, new ReadCompletionHandler(result, buf));
    }

    @Override
    public void failed(Throwable exc, Void attachment) {
        //
        System.out.println("accept failed");
    }
}

class ReadCompletionHandler implements CompletionHandler<Integer, ByteBuffer> {

    private static final String ReadHeadState = "head";
    private static final String ReadBodyState = "body";

    private ByteBuffer head;

    private String state = ReadHeadState;

    private AsynchronousSocketChannel asyClient;

    private WriteCompletionHandler0 writeHandler;

    public ReadCompletionHandler(AsynchronousSocketChannel asyClient, ByteBuffer head) {
        this.asyClient = asyClient;
        this.head = head;
        writeHandler = new WriteCompletionHandler0(asyClient);
    }

    @Override
    public void completed(Integer result, ByteBuffer buffer) {
        if(result == -1) return;
        if(buffer.remaining() == 0) {
            if(state == ReadHeadState) {
                String headStr = new String(head.array());
                head.clear();
                ByteBuffer body = ByteBuffer.allocate(Integer.parseInt(headStr));
                state = ReadBodyState;
                asyClient.read(body, body, this);
            }
            else {
                String body = new String(buffer.array());
                processMsg(body);

                String content = "response to client";
                String writeStr = String.format("%05d%s", content.getBytes().length, content);
                ByteBuffer writeBuf = ByteBuffer.wrap(writeStr.getBytes());
                writeHandler.completed(0, writeBuf);

                state = ReadHeadState;
                asyClient.read(head, head, this);
            }
        }
    }

    @Override
    public void failed(Throwable exc, ByteBuffer attachment) {

    }

    private void processMsg(String msg) {
        System.out.println("read a msg:"+msg);
    }
}

class WriteCompletionHandler0 implements CompletionHandler<Integer, ByteBuffer> {

    private MessageQueue<ByteBuffer> msgQueue = new MessageQueue();

    private AsynchronousSocketChannel asyChannel;

    public WriteCompletionHandler0(AsynchronousSocketChannel asyChannel) {
        this.asyChannel = asyChannel;
    }

    @Override
    public void completed(Integer result, ByteBuffer lastBuffer) {
        if(lastBuffer.remaining() == 0) {
            ByteBuffer b = msgQueue.poll();;
            if(b != null)
                asyChannel.write(b, b, this);
        } else {
            asyChannel.write(lastBuffer, lastBuffer, this);
        }

    }

    @Override
    public void failed(Throwable exc, ByteBuffer lastBuffer) {
        //IOException Broken pipe
        System.out.println("write failed");
    }

    public void addMsg(byte[] msg) {
        msgQueue.add(ByteBuffer.wrap(msg));
    }

    public ByteBuffer getMsg() {
        return msgQueue.poll();
    }
}

public class TestAIOServer {

    public static void main(String args[]) throws IOException, InterruptedException {

        ExecutorService service = Executors.newFixedThreadPool(3);

        AsynchronousChannelGroup group = AsynchronousChannelGroup.withThreadPool(service);

        AsynchronousServerSocketChannel assc  = AsynchronousServerSocketChannel.open(group);

        assc.bind(new InetSocketAddress("172.31.1.187", 8801));

        assc.accept(null, new AcceptCompletionHandler0(assc));
        //CompletionHandler

        //AsynchronousSocketChannel;
        Object l = new Object();
        synchronized (l) {
            l.wait();
        }


    }

}

