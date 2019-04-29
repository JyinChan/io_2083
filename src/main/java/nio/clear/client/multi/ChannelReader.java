package nio.clear.client.multi;

import java.nio.ByteBuffer;

public interface ChannelReader {

    void read(ByteBuffer readBuf);
}
