package nio.clear;

import nio.clear.server.*;
import org.apache.log4j.PropertyConfigurator;

public class Test2 implements IoListener {

    public static void main(String[] args) {
        PropertyConfigurator.configure("conf/log4j.properties");

        Reactor reactor = new Reactor();
        reactor.start();
        RegFuture regFuture = reactor.connect("localhost", 8801, SupportAsyncResultNioC.class, new Test2());

    }

    @Override
    public void connectionOpened(NioConnection connection) {
        new Thread(() -> {
            SupportAsyncResultNioC c = (SupportAsyncResultNioC) connection;
            while (true) {
                WriteReadFuture wrf = c.writeAndRead("00011hello world", "OK");
                System.out.println(wrf.get());
                c.write("00011hello world");
                try {
                    Thread.sleep(12000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        connection.keepAlive(NioConnection.WRITE_IDLE_STATUS, 3000, "00002HB", "OK", true, 5000);
    }

    @Override
    public void connectionClosed(NioConnection connection) {
        //
    }

    @Override
    public void messageReceived(NioConnection connection, String msg) {
        System.out.println("received: " + msg);
    }

    @Override
    public void onTimeout(NioConnection connection, int timeoutType) {
        System.out.println("timeout");
    }

    @Override
    public void exceptionCaught(NioConnection connection, Exception e) {
        e.printStackTrace();
    }
}
