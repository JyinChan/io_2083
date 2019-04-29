package aio.begin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.AsynchronousChannelProvider;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class AcceptCompletionHandler implements CompletionHandler<AsynchronousSocketChannel, Void> {

    AsynchronousServerSocketChannel asyServer;

    public AcceptCompletionHandler(AsynchronousServerSocketChannel asyServer) {
        this.asyServer = asyServer;
    }
    @Override
    public void completed(AsynchronousSocketChannel result, Void attachment) {
        asyServer.accept(null, new AcceptCompletionHandler(asyServer));
        CompletionHandler<Integer, ByteBuffer> w = new WriteCompletionHandler(result);
//        byte[] b = new byte[10000];int i=0;
//        for(; i<100; i++) {
//            try {
//                ByteBuffer buf = ByteBuffer.wrap(b);
//                result.write(buf, null, w);
//            } catch (WritePendingException e) {
//                i--;
//            }
//        }
        byte[] b = new byte[10000];
        for(int i=0; i<1000; i++)
        ((WriteCompletionHandler) w).addMsg(b);
        w.completed(0, ((WriteCompletionHandler) w).getMsg());
    }

    @Override
    public void failed(Throwable exc, Void attachment) {
        //
        System.out.println("accept failed");
    }
}

class WriteCompletionHandler implements CompletionHandler<Integer, ByteBuffer> {

    private MessageQueue<ByteBuffer> msgQueue = new MessageQueue();

    private AsynchronousSocketChannel asyChannel;

    public WriteCompletionHandler(AsynchronousSocketChannel asyChannel) {
        this.asyChannel = asyChannel;
    }

    @Override
    public void completed(Integer result, ByteBuffer lastBuffer) {
        if(lastBuffer.remaining() == 0) {
            ByteBuffer b = msgQueue.poll();
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

public class AIOServer {

    public static void main(String args[]) throws IOException, InterruptedException {

        ExecutorService service = Executors.newFixedThreadPool(3);

        AsynchronousChannelGroup group = AsynchronousChannelGroup.withThreadPool(service);

        AsynchronousServerSocketChannel assc  = AsynchronousServerSocketChannel.open(group);

        AsynchronousChannelProvider p = assc.provider();

        assc.bind(new InetSocketAddress("localhost", 8881));

        assc.accept(null, new AcceptCompletionHandler(assc));
        //CompletionHandler

        //AsynchronousSocketChannel;
        Object l = new Object();
        synchronized (l) {
            l.wait();
        }


    }

    static class Sender implements Runnable {

        private Map<String, AsynchronousSocketChannel> clientChannelMap = new ConcurrentHashMap<>();

        @Override
        public void run() {
            Scanner scan = new Scanner(System.in);
            String line ;
            CompletionHandler<Integer, ByteBuffer> handler = null;//new WriteCompletionHandler();
            while(scan.hasNext()) {
                line = scan.nextLine();
                ByteBuffer b = ByteBuffer.wrap(line.getBytes());
                for(Iterator<String> keySetIt = clientChannelMap.keySet().iterator(); keySetIt.hasNext(); ) {
                    AsynchronousSocketChannel channel = clientChannelMap.get(keySetIt.next());
                    handler = new WriteCompletionHandler(channel);
                    channel.write(b, null, handler);
                }
            }
        }

        public void addClient(String user, AsynchronousSocketChannel channel) {
            //ensure user is unique
            clientChannelMap.put(user, channel);
        }
    }
}
