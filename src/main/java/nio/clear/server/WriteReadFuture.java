package nio.clear.server;

import java.nio.ByteBuffer;

public class WriteReadFuture extends WriteFuture {

    private ReadFuture readFuture;
    private WriteFuture writeFuture;

    WriteReadFuture(String writeMsg, String readTarget) {
        writeFuture = new WriteFuture(writeMsg);
        readFuture = new ReadFuture(readTarget);
    }

    @Override
    protected ByteBuffer getBuf() {
        return writeFuture.getBuf();
    }

    @Override
    protected void done(boolean succeed) {
        writeFuture.done(succeed);
        readFuture.done(succeed);
    }

    @Override
    public boolean get() {
        return writeFuture.get() && readFuture.get();
    }

    public boolean isWrote() {
        return writeFuture.get();
    }

    public boolean isRead() {
        return readFuture.get();
    }

    public WriteFuture getWriteFuture() {
        return writeFuture;
    }

    public ReadFuture getReadFuture() {
        return readFuture;
    }

}
