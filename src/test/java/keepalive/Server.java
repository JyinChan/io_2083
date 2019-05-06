package keepalive;

import org.apache.log4j.PropertyConfigurator;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Server extends Thread {

    public static void main(String[] args) throws Exception {

        PropertyConfigurator.configure("conf/log4j.properties");
        PropertyConfigurator.configureAndWatch("conf/log4j.properties", 60000);

        ServerSocket socket = new ServerSocket();
        socket.bind(new InetSocketAddress("localhost", 8801));

        AliveKeeper keeper = new AliveKeeper();

        while (true) {
            Socket clientSocket = socket.accept();
            Connection connection =
                    new Connection(clientSocket, keeper, HeartBeat.buildResponse("HB", "HBR".getBytes()).withTTL(9000));
            new Server(connection).start();
        }

    }

    private Connection connection;

    private Server(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        connection.keepAlive();
        connection.read(1000);
    }
}
