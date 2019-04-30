package nio.clear.server;

import java.util.function.Consumer;

public class RegFuture {

    private final Object lock = new Object();

    private NioConnection connection;
    private boolean isDone;

    private Consumer<Object> consumer;

    void done(NioConnection connection) {
        synchronized (lock) {
            isDone = true;
            this.connection = connection;
            if(consumer != null) {
                consumer.accept(connection);
            }
            lock.notifyAll();
        }
    }

    public void whenComplete(Consumer<Object> consumer) {
        synchronized (lock) {
            if(isDone) {
                consumer.accept(connection);
            } else {
                this.consumer = consumer;
            }
        }
    }

    public NioConnection get() {
        synchronized (lock) {
            if(isDone) return connection;
            try {
                lock.wait();
            } catch (InterruptedException ignore) {

            }
            return connection;
        }
    }
}
