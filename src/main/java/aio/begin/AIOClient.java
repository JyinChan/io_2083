package aio.begin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.atomic.AtomicInteger;

public class AIOClient {

    public static void main(String args[]) throws IOException ,InterruptedException{

        AsynchronousSocketChannel asc = AsynchronousSocketChannel.open();
        asc.connect(new InetSocketAddress("localhost", 8881));

        Thread.sleep(2000);

        ByteBuffer b = ByteBuffer.allocate(100);
        asc.read(b, b, new ReadCompletionHandler(asc));

        Object l = new Object();

        synchronized (l) {
            l.wait();
        }
    }

    static class ReadCompletionHandler implements CompletionHandler<Integer, ByteBuffer> {

        private static final String ReadHeadState = "head";
        private static final String ReadBodyState = "body";

        private ByteBuffer head;

        private String state = ReadHeadState;

        private AsynchronousSocketChannel asyClient;

        private AtomicInteger i = new AtomicInteger();

        public ReadCompletionHandler(AsynchronousSocketChannel asyClient) {
            this.asyClient = asyClient;
            head = ByteBuffer.allocate(5);
        }

        @Override
        public void completed(Integer result, ByteBuffer buffer) {
            //result = -1 if connection is broken
            if(result == -1) return;
            System.out.println(i.addAndGet(result));
            buffer.clear();
            //asyClient.read(buffer, buffer, this);
            //
//            if(buffer.remaining() == 0) {
//                if(state == ReadHeadState) {
//                    String head = new String(buffer.array());
//                    ByteBuffer body = ByteBuffer.allocate(Integer.parseInt(head));
//                    state = ReadBodyState;
//                    asyClient.read(body, body, this);
//                }
//                else {
//                    String body = new String(buffer.array());
//                    processMsg(body);
//                    state = ReadHeadState;
//                    asyClient.read(head, head, this);
//                }
//            }
        }

        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {

        }

        private void processMsg(String msg) { System.out.println("read a msg:"+msg); }
    }

}
