package tcp.delay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class DelayTest implements Runnable {

    Socket socket = null;

    OutputStream out = null;
    InputStream in = null;

    private String host;

    private int port;

    public DelayTest() {}

    public DelayTest(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String read() {

        byte header[] = new byte[5];
        byte body[];
        try {
            if(in.read(header) == -1)
                throw new IOException("read return -1");
            int len = Integer.parseInt(new String(header));
            body = new byte[len];
            if(in.read(body) == -1)
                throw new IOException("read return -1");
        } catch(IOException e) {
            //LOG.error("read error {}", e.getMessage());
            return null;
        }
        return new String(body);
    }

    volatile boolean isRun = true;

    private void close() {
        isRun = false;
        try {
            socket.close();
        } catch(IOException e) {
            //LOG.error("oio close {}", e.getMessage());
        }
        //LOG.info("oio closed");
    }

    public boolean write(String message) {
        message = String.format("%05d%s", message.getBytes().length, message);
        //LOG.info("send {}", message);
        try {
            out.write(message.getBytes());
        } catch(IOException e) {
            //LOG.error("write error {}", e.getMessage());
            return false;
        }
        return true;
    }

    //PING-PONG模式
    private void pingpongTest() {
        write("error: unreported exception IOException; must be caught or declared to be thrownping pong test!!!");
        String msg = read();
        try {
            Thread.sleep(1);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
        System.out.println(msg);
    }

    //订阅模式仍然会出现ACK延迟，在出现延迟之后的数据包传输中，大于170？的数据包不出现ACK延迟
    private void subscribeTest() {
        write("subscribe mode test!!!");
        try {
            Thread.sleep(20);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
//        count++;
//        if(count == 100) {
//            write("error: unreported exception IOException; must be caught or declared to be thrown" +
//                    "error: unreported exception IOException; must be caught or declared to be thrown" +
//                    "error: unreported exception IOException; must be caught or declared to be thrown");
//        }
    }

    //1、nagle模式下，什么时候不等待ACK
    //该模式下
    //如果发送内容大于1个MSS， 立即发送；

    //如果之前没有包未被确认， 立即发送；

    //如果之前有包未被确认， 缓存发送内容；
    //但在发送内容大于1个MSS而又发送了1个大包后，无论前面是否有无未被确认的ack，剩余的小包会被发送；

    //如果收到ack， 立即发送缓存的内容

    //前30个消息是w-r-w-r状态，进入ping-pong模式，后面消息客户端w状态，服务端r状态
    //现象：1：当服务端只读时，返回一个延时ACK（服务端sleep 30ms再读，否则不延时）；2：在之后的数据包中，当数据稍大时(>170?)，ACK不延时，反之，ACK延时
    //分析：其实2中的模式相当于订阅模式...
    private int count = 0;
    private void ppsTest() {
        write("pps test hello world!");
        if(count++ <30) {
            String msg = read();
            System.out.println(msg);
        }
        try {
            Thread.sleep(1);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

    //2、发起端为什么不出现ack延迟??
    private void acceptorTest() {
        String msg = read();
        if(msg != null) {
            try {
                Thread.sleep(30);
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
            write("response a msg from client");
        }
        System.out.println(msg);
    }

    public static void main(String args[]) throws Exception {
        DelayTest test = new DelayTest("150.19.16.17", 7777);
        Thread t = new Thread(test);
        t.start();
        t.join();
    }

    public void run() {

        socket = new Socket();

        try {
            //oio.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(host, port));
            in = socket.getInputStream();
            out = socket.getOutputStream();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }

        while(isRun) {
            ppsTest();
        }
    }

    public static int[] test = new int[1];

}


