package nio.begin.reader;

import java.nio.channels.SocketChannel;

public interface ReadState {

    String read(SocketChannel channel) throws Exception;

}
