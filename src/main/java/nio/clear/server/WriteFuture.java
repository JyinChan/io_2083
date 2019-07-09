package nio.clear.server;

import java.nio.ByteBuffer;

public class WriteFuture {

    private final Object lock = new Object();

    private Boolean result;

    private ByteBuffer writeBuf;

    WriteFuture() {}

    WriteFuture(String writeMsg) {
        this.writeBuf = ByteBuffer.wrap(writeMsg.getBytes());
    }

    protected ByteBuffer getBuf() {
        return writeBuf;
    }

    protected void done(boolean succeed) {
        synchronized (lock) {
            this.result = succeed;
            lock.notifyAll();
        }
    }

    public boolean get() {

        synchronized (lock) {
            if (result != null) return result;
            try {
                lock.wait();
            } catch (InterruptedException ie) {
                //return false;
            }
            return result;
        }
    }

}
