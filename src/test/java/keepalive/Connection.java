package keepalive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.Executors;

public class Connection {

    private final static Logger logger = LoggerFactory.getLogger(Connection.class);

    private Socket socket;
    private HeartBeat heartBeat;
    private HeartBeatTransportImpl transport;
    private KeepAliveListener listener;
    private AliveKeeper keeper;

    public Connection(Socket socket, AliveKeeper keeper, HeartBeat heartBeat) {
        this.socket = socket;
        this.keeper = keeper;
        this.heartBeat = heartBeat;
    }

    public void read(int loop) {
        try {
            InputStream in = socket.getInputStream();
            DataInputStream dataIn = new DataInputStream(in);
            while(loop-- > 0) {
                String s = dataIn.readUTF();
                onReceive(s);
            }
        } catch (IOException e) {
            e.printStackTrace();
            close();
        }
    }

    public void write(String s) {
        try {
            OutputStream out = socket.getOutputStream();
            DataOutputStream dataOut = new DataOutputStream(out);
            dataOut.writeUTF(s);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            close();
        }
    }

    public void close() {
        try { socket.close(); } catch (IOException ignore) {}
        listener.close();
    }

    public void keepAlive() {
        //Executors.newSingleThreadExecutor().submit(() -> {
            try {
                long keepAliveId = keeper.applyKeepAliveId();
                HeartBeatTransportImpl transport = new HeartBeatTransportImpl();
                listener = keeper.keepAlive(keepAliveId, heartBeat, transport);
                this.transport = transport;

                //HeartBeat response = listener.listen();
                //log("ttl: " + response.getTTL());
            } catch (Exception e) {
                e.printStackTrace();
            }
       // });
    }

    private void log(String s) {
        logger.debug(s);
    }

    private void onReceive(String s) {
        log("received:" + s);
        if(transport == null || !transport.isHB(s)) {
            //directly dump
            //log("received:" + s);
        }
    }

    class HeartBeatTransportImpl implements HeartBeatTransport {

        private HeartBeatObserver observer;

        @Override
        public void send(byte[] hbBytes) {
            log("send HB...");
            write(new String(hbBytes));
        }

        @Override
        public void addObserver(HeartBeatObserver hbObserver) {
            observer = hbObserver;
        }

        @Override
        public void destroy() {
            log("destroy...");
            close();
        }

        boolean isHB(String s) {
            return observer.update(s);
        }
    }
}
