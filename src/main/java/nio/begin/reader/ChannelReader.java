package nio.begin.reader;

import nio.begin.Protocol;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ChannelReader implements Reader {

    private ReadState state;

    private ReadState readHeaderState;

    private ReadState readBodyState;

    private ByteBuffer header;

    private ByteBuffer body;

    private SocketChannel channel;

    public ChannelReader(SocketChannel channel) {
        this.channel = channel;
        header = Protocol.allocateHeader();
        readHeaderState = new ReadHeaderState(this);
        readBodyState = new ReadBodyState(this);
        state = readHeaderState;
    }

    @Override
    public String read() throws Exception {
        return state.read(channel);
    }

    protected ByteBuffer getHeader() {
        return header;
    }

    protected ByteBuffer getBody() {
        return body;
    }

    protected void setState(ReadState state) {
        this.state = state;
    }

    protected ReadState getReadHeaderState() {
        return readHeaderState;
    }

    protected ReadState getReadBodyState() {
        return readBodyState;
    }

    protected void setBody(ByteBuffer body) {
        this.body = body;
    }

}
