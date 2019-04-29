package Util;

public class MemoryListener implements Runnable {

    @Override
    public void run() {
        for(;;) {
            long free = Runtime.getRuntime().freeMemory();
            //long max = Runtime.getRuntime().maxMemory();
            long total = Runtime.getRuntime().totalMemory();
            System.out.printf("%s:%s\n", "use", total-free);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }
    }
}
