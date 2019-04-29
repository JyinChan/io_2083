package nio.small.buffer;

import java.nio.ByteBuffer;

public interface WritableBuf {

    ByteBuffer getBuffer();

    void finishWrite();

    void beginWrite();
}
