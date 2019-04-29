package tcp.close;

import java.net.InetSocketAddress;
import java.net.Socket;

public class Client {

    public static void main(String[] args) throws Exception {

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("localhost", 8888));
        test1(socket);
    }

    private static void test1(Socket socket) throws Exception {
        byte[] b = new byte[100];
        while(true) {
            socket.getOutputStream().write(b);
            Thread.sleep(100);
        }
    }

}
