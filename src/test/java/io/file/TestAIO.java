package io.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

//
//AIO从请求开始到完成请求每条消耗5ms
//从执行到执行结束每条消耗1~2ms
//从并发量来看，AIO是大于BIO，BIO需要线程消耗(线程切换，线程同步)，线程的数量是有限制的。
//从吞吐量来看，BIO是大于AIO，AIO每条IO操作的完成过程至少消耗5ms，而BIO每次IO操作包括打开IO完成过程只需1~2ms.
//总的来说，在并发量较少的情况下（如1000，可能更大，BIO也是可以承受的），使用BIO；大数量数据传输情况下，使用BIO
//并发量较大情况下，使用AIO
//
public class TestAIO {

    public static void main(String args[]) throws IOException , InterruptedException {

        Path path = Paths.get("conf/log4j.properties");
        ReadCompletionHandler handler = new ReadCompletionHandler();

        final int c = 1000;
        AsynchronousFileChannel channels[] = new AsynchronousFileChannel[c];
        ByteBuffer rs[] = new ByteBuffer[c];

        ExecutorService e = Executors.newFixedThreadPool(10);
        Set<OpenOption> set = new HashSet<OpenOption>(0);
        FileAttribute<?>[] NO_ATTRIBUTES = new FileAttribute[0];

        for(int i=0; i<c; i++) {
            channels[i] = AsynchronousFileChannel.open(path,set,e,NO_ATTRIBUTES);
            rs[i] = ByteBuffer.allocate(80000);
        }
        System.out.println("start read:" + System.currentTimeMillis());
        for(int i=0; i<c; i++) {
            //System.out.println(System.currentTimeMillis());
            channels[i].read(rs[i], 0, channels[i], handler);
        }
        System.out.println("end read:" + System.currentTimeMillis());
        Object l = new Object();
        synchronized (l) {
            l.wait();
        }
    }

    static class ReadCompletionHandler implements CompletionHandler<Integer, AsynchronousFileChannel> {

        static AtomicInteger i = new AtomicInteger();

        static volatile long s = System.currentTimeMillis();

        static volatile int last = 0;

        @Override
        public void completed(Integer result, AsynchronousFileChannel attachment) {
            //System.out.println(System.currentTimeMillis());
            i.incrementAndGet();
            if(System.currentTimeMillis() - s >= 1000) {
                s = System.currentTimeMillis();
                int temp = last;
                last = i.get();
                System.out.println(last-temp+"/s");
            }
            //attachment.read(ByteBuffer.allocate(100), 0, attachment, this);
        }

        @Override
        public void failed(Throwable exc, AsynchronousFileChannel attachment) {

        }
    }
}
