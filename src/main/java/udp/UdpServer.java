package udp;

import java.net.*;

public class UdpServer {

    public static void main(String args[]) throws Exception {
//        DatagramSocket socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 8801));
//        socket.setBroadcast(true);
//        while(true) {
//
//            DatagramPacket packet = new DatagramPacket(new byte[1], 1);
//            socket.receive(packet);
//            System.out.println("received-"+new String(packet.getData()));
//            System.out.println("packet from-"+packet.getSocketAddress());
//            socket.send(packet);
//
//            DatagramPacket packet1 = new DatagramPacket("hello".getBytes(), 5);
//            socket.send(packet1);
//            Thread.sleep(1000);
//
//        }

        MulticastSocket multicastSocket = new MulticastSocket(8801);
        multicastSocket.joinGroup(InetAddress.getByName("224.0.0.1"));
        DatagramPacket packet = new DatagramPacket(new byte[15], 0 , 15);
        while(true) {
            multicastSocket.receive(packet);
            System.out.println(new String(packet.getData()));
            Thread.sleep(1000);
        }
    }

}
