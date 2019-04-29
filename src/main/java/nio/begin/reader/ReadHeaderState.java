package nio.begin.reader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ReadHeaderState implements ReadState {

    private ChannelReader reader;

    public ReadHeaderState(ChannelReader reader) {
        this.reader = reader;
    }

    @Override
    public String read(SocketChannel channel) throws Exception {
        ByteBuffer header = reader.getHeader();
        int x = channel.read(header);
        if(x == -1) throw new IOException("-1");

        if(!header.hasRemaining()) {

            reader.setState(reader.getReadBodyState());

            String head =  new String(header.array());
            int bodyLen = Integer.parseInt(head);
            reader.setBody(ByteBuffer.allocate(bodyLen));

            header.clear();

            return reader.read();
        }
        return null;
    }

}
