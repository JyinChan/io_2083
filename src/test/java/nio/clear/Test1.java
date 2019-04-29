package nio.clear;

import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

public class Test1 {

    private static AtomicReference<Test1> ar = new AtomicReference<>();

    public static void main(String args[]) throws InterruptedException {

        HashSet<String> set = new HashSet<>();
        set.add("1");
        set.add("2");
        set.add("3");
        synchronized (set) {
            for (Iterator<String> it = set.iterator(); it.hasNext(); ) {
                it.next();
                it.remove();
            }
        }
    }

    private static Test1 test1;

    public static Test1 getInstance0() {
        if(test1 == null) {
            synchronized (Object.class) {
                if(test1 == null) {
                    Test1 temp = new Test1();
                    test1 = temp;
                }
            }
        }
        return test1;
    }

    public static Test1 getInstance() {
        for( ; ; ) {
            Test1 test1 = ar.get();
            if(test1 != null)
                return test1;
            test1 = new Test1();
            if(ar.compareAndSet(null, test1))
                return test1;
        }
    }
}
