package udp;

import java.net.*;
import java.nio.ByteBuffer;

public class UdpClient {

    public static void main(String args[]) throws Exception {

//        DatagramSocket socket = new DatagramSocket();
//        //socket.connect(new InetSocketAddress("127.0.0.1", 8801));
//        while(true) {
//            DatagramPacket packet = new DatagramPacket("A".getBytes(), 0, 1, new InetSocketAddress("127.0.0.1", 8801));
//            socket.send(packet);
//            socket.receive(packet);
//            System.out.println(new String(packet.getData()));
//            Thread.sleep(1000);
//            //socket.
//        }

        MulticastSocket multicastSocket = new MulticastSocket();
        DatagramPacket packet = new DatagramPacket("hello".getBytes(), 0, 5, InetAddress.getByName("224.0.0.1"), 8801);
        int cnt=0;
        while(cnt++<20) {
            multicastSocket.send(packet);
            Thread.sleep(100);
        }
    }
}
