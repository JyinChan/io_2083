package nio.clear;

import nio.clear.server.GeneralNioConnection;
import nio.clear.server.IoListener;
import nio.clear.server.NioConnection;
import nio.clear.server.Reactor;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Test0 implements IoListener {

    private final static Logger logger = LoggerFactory.getLogger(Test0.class);

    /*
        bind
        connect
     */
    public static void main(String args[]) throws Throwable {
        PropertyConfigurator.configure("conf/log4j.properties");
        Reactor reactor = new Reactor();
        for(int i=8809;i<8816;i++)
        reactor.addBind("127.0.0.1", i, 30, GeneralNioConnection.class, Test0.class);

        reactor.start();

//        reactor.scheduledConnect("127.0.0.1", 8819, GeneralNioConnection.class, new Test0());
//        Thread.sleep(5000);
//        logger.debug("counter[{}]", counter);

    }

    private GeneralNioConnection conn;

    @Override
    public void connectionOpened(NioConnection connection) {
        logger.debug("connection opened");
        connection.config(NioConnection.READ_IDLE_STATUS, 5000, "00000", "00002hi", false, 0);
        conn = (GeneralNioConnection) connection;
    }

    @Override
    public void connectionClosed(NioConnection connection) {
        logger.debug("connection closed");
    }

    @Override
    public void messageReceived(NioConnection connection, String msg) {
        logger.debug("received: [{}]", msg);
        //Reactor.execute(() -> handle0(msg));
        //((GeneralNioConnection)connection).suspendRead(true);
        handle0(msg);
    }

    private void handle0(String msg) {
        for(int i=0; i<10000000; i++);
        conn.write(String.format("%05d%s", msg.getBytes().length, msg));
    }

    @Override
    public void onTimeout(NioConnection connection, int timeoutType) {
        logger.debug("timeout");
        conn.close();
    }

    @Override
    public void exceptionCaught(NioConnection connection, Exception e) {
        logger.error("", e);
    }
}
