package oio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SocketClient implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(SocketClient.class);

    private volatile boolean isRun = true;

    private Socket socket = null;
    private OutputStream out = null;
    private InputStream in;

    private String host = "127.0.0.1";
    private int port = 8801;

    public SocketClient() {}

    public SocketClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void run() {

        socket = new Socket();

        connect.incrementAndGet();

        try {
            //
            //socket.setSoLinger(true, 0);
            //
            socket.setTcpNoDelay(false);
            socket.connect(new InetSocketAddress(host, port));
            in = socket.getInputStream();
            out = socket.getOutputStream();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            connect.decrementAndGet();
            return;
        }

        //try { Thread.sleep(100); } catch (Exception ignore) {}

        //if(!write("hello")) connect.decrementAndGet();

        try { Thread.sleep(200); } catch (InterruptedException ignore) {}

        close();

//        while(/*isRun*/all.incrementAndGet()<=3000000) {
//            boolean wFlag = write("write a msg from client to server!!!");
//            if(wFlag)
//                ws.incrementAndGet();
//            else
//                wf.incrementAndGet();
//            String msg = read();
//            if(msg == null) {
//                rf.incrementAndGet();
//                close();
//            }
//            else {
//                rs.incrementAndGet();
//            }
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException ignore) {}
//            //LOG.info("message from server response: {}", msg);
//        }
    }

    private void close() {
        isRun = false;
        try {
            socket.close();
        } catch(IOException e) {
            LOG.error("oio close {}", e.getMessage());
        }
        LOG.info("oio closed");
    }

    private boolean write(String msg) {
        return SocketServer.write(out, msg);
    }

    private String read() {
        return SocketServer.read(in);
    }

    public static void main(String args[]) {
        //
        ExecutorService es = Executors.newCachedThreadPool();
        for(int i=0; i<1; i++) {
            SocketClient client = new SocketClient();
            es.execute(client);
        }

        try { Thread.sleep(5000); } catch (Exception ignore) {}
        System.out.println(connect.get());
/*
        es.execute(new Runnable() {
            @Override
            public void run() {
                int index = 0;
                int lastWs;
                int lastRs;
                int tWs = 0;
                int tRs = 0;
                System.out.printf("%8s%5s%5s%5s%5s%10s%10s\n", "index", "ws", "rs", "wf", "rf", "wSpeed", "rSpeed");
                while(all.get() <= 3000000) {
                    index++;
                    lastWs = tWs;lastRs=tRs;
                    tWs = ws.get();tRs = rs.get();
                    System.out.printf("%5d%8d%8d%8d%8d%8d%8d\n", index, tWs, tRs, wf.get(), rf.get(), tWs-lastWs, tRs-lastRs);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                System.out.println("connect success:"+connect.get());
            }
        });*/

    }

    private static AtomicInteger connect = new AtomicInteger();
    private static AtomicInteger ws = new AtomicInteger();
    private static AtomicInteger rs = new AtomicInteger();
    private static AtomicInteger wf = new AtomicInteger();
    private static AtomicInteger rf = new AtomicInteger();
    private static AtomicInteger all = new AtomicInteger();

}

