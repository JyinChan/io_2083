package nio.small.coder;

import nio.small.buffer.ReadableBuf;

import java.util.List;

public interface MsgDecoder {

    void decode(ReadableBuf buf, List<Object> results);
}
