package nio.begin;

import java.nio.ByteBuffer;

public class Protocol {

    public final static int Head_Len = 5;

    public static ByteBuffer formatMessage(String content) {
        byte[] b = content.getBytes();
        String m = String.format("%05d%s", b.length, content);
        ByteBuffer buffer = ByteBuffer.wrap(m.getBytes());
        return buffer;
    }

    public static ByteBuffer allocateHeader() {
        return ByteBuffer.allocate(Head_Len);
    }
}
