package nio.small.coder;

import nio.small.buffer.ReadableBuf;

import java.util.List;

public class DefaultMsgDecoder implements MsgDecoder {

    @Override
    public void decode(ReadableBuf buf, List<Object> results) {
        while (buf.readableBytes() > 16) {
            byte[] o = new byte[16];
            buf.read(o, 0 ,16);
            results.add(o);
        }
    }
}
