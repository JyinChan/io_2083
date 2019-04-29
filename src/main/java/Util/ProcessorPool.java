package Util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ProcessorPool implements Runnable {

    private final ArrayBlockingQueue<Runnable> taskQueue = new ArrayBlockingQueue<>(100);

//    private Object att;
    public ProcessorPool() {
    }

    @Override
    public void run() {
        for( ; ; ) {
            try {
                Runnable r = taskQueue.poll(120, TimeUnit.SECONDS);
                if(r != null) r.run();
                else break; //else if att is closed
            } catch (Exception e) {

            }
        }
    }
}
