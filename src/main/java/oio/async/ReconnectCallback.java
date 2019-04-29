package oio.async;

import java.io.IOException;
import java.net.Socket;

public interface ReconnectCallback {

    void call(Socket newSocket, int newSocketVersion) throws IOException;

}
