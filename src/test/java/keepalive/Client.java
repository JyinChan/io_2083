package keepalive;

import org.apache.log4j.PropertyConfigurator;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Client extends Thread {

    public static void main(String args[]) throws Exception {

        PropertyConfigurator.configure("conf/log4j.properties");
        PropertyConfigurator.configureAndWatch("conf/log4j.properties", 60000);

        for(int i=1; i<5; i++) {
            Client client = new Client(i);
            client.connect("localhost", 8801);
            client.start();
        }

        Thread.sleep(5000);
        keeper.stop();

    }

    private static AliveKeeper keeper = new AliveKeeper();
    private static HeartBeat heartBeat = HeartBeat.buildRequest("HB".getBytes(), "HBR").withTTL(9000);

    private Connection connection;
    private int loop;

    public Client(int loop) {
        this.loop = loop;
    }

    public void connect(String host, int ip) throws IOException  {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, ip));
        connection = new Connection(socket, keeper, heartBeat);
    }

    @Override
    public void run() {
        connection.keepAlive();
        connection.read(loop);
        try {
            Thread.sleep(100_1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}

