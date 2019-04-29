package nio.small.msg;

import java.nio.ByteBuffer;

public class WriteMsg {

    private ByteBuffer buffer;
    private String origin;

    private WriteMsg(String origin) {
        this.origin = origin;
    }
    public ByteBuffer getBuffer() {
        return buffer;
    }
    public void setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }
    public String getOrigin() { return origin; }

    public static WriteMsg create(String origin) {
        if(origin == null)
            throw new IllegalArgumentException();
        return new WriteMsg(origin);
    }
}
