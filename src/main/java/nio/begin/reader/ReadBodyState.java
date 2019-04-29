package nio.begin.reader;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ReadBodyState implements ReadState {

    private ChannelReader reader;

    public ReadBodyState(ChannelReader reader) {
        this.reader = reader;
    }

    @Override
    public String read(SocketChannel channel) throws Exception {
        ByteBuffer body = reader.getBody();
        channel.read(body);
        if(!body.hasRemaining()) {
            reader.setState(reader.getReadHeaderState());
            return new String(body.array());
        }
        return null;
    }
}
