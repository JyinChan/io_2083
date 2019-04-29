package oio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketServer implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(SocketServer.class);

    public static void main(String args[]) throws IOException {
        //new Thread(new MemoryListener()).start();
        //for(int i=8809;i<8816;i++)
        start("localhost", 8801);
    }

    public static void start(String host, int port) throws IOException  {
        final ServerSocket server = new ServerSocket();
        server.bind(new InetSocketAddress(host, port));
        LOG.info("start accept");
        new Thread(() -> {
            while(true) {
                try {
                    Socket socket = server.accept();
                    //oio.setTcpNoDelay(true);
                    //socket.setSoTimeout(60);
                    LOG.info("connected from host [{}] and port [{}]", socket.getInetAddress(), socket.getPort());
                    SocketServer tc = new SocketServer(socket);
                    new Thread(tc).start();
                } catch (IOException ioe) {
                }
            }
        }).start();
    }

    public static boolean write(OutputStream writer, String message) {
        message = String.format("%05d%s", message.getBytes().length, message);
        //LOG.info("send {}", message);
        try {
            writer.write(message.getBytes());
        } catch(IOException e) {
            LOG.error("", e);
            return false;
        }
        return true;
    }

    public static String read(InputStream reader) {

        byte header[] = new byte[5];
        byte body[];
        try {
            if(reader.read(header) == -1)
                throw new IOException("read return -1");

            int len = Integer.parseInt(new String(header));
            body = new byte[len];
            if(reader.read(body) == -1)
                throw new IOException("read return -1");
        } catch(IOException e) {
            LOG.error("", e);
            return null;
        }
        return new String(body);
    }

    private Socket socket;

    private volatile boolean isRun = true;

    public SocketServer(Socket socket ) {
        this.socket = socket;
    }

    public void run() {
        InputStream reader ;
        OutputStream writer ;
        try {
            reader = socket.getInputStream();
            writer = socket.getOutputStream();
        } catch (IOException e) {
            return;
        }

        while(true) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignore) {}
            write(writer, "OK");
            //LOG.info("read start");
            String msg = read(reader);
            //LOG.info("read end");
            if(msg != null) {
                write(writer, "OK");
            } else {
                close();
                return;
            }
            LOG.info("message from client : {}", msg);
        }

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
}
