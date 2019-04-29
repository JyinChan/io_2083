package io.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

//
//从执行到执行结束每条消耗1~2ms
//
public class TestBIO {

    public static void main(String args[]) {
        long s = System.currentTimeMillis();
        ExecutorService executors = Executors.newFixedThreadPool(1000);
        //System.out.println(System.currentTimeMillis()-s);
        for(int i=0; i<1000; i++) {
            //System.out.println(System.currentTimeMillis());
            executors.execute(new ReadFileRunner());
        }
    }

    static class ReadFileRunner implements Runnable {

        static AtomicInteger i = new AtomicInteger();

        static volatile long s = System.currentTimeMillis();

        static volatile int last = 0;

        public void run() {
            File file = new File("conf/test.txt");
            try {
                InputStream in = new FileInputStream(file);
                byte[] b = new byte[80000];
                in.read(b);
            } catch (IOException e) {
                e.printStackTrace();
            }
            //System.out.println(System.currentTimeMillis());
            i.incrementAndGet();
            if(System.currentTimeMillis() - s > 100) {
                s = System.currentTimeMillis();
                int temp = last;
                last = i.get();
                System.out.println(last-temp+"/100s");
            }
        }

    }
}
