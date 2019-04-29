package tcp.delay;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

public class DelayServer {

    public DelayServer () { }

    public static boolean write(OutputStream writer, String message) {
        message = String.format("%05d%s", message.getBytes().length, message);
        //LOG.info("send {}", message);
        try {
            writer.write(message.getBytes());
        } catch(IOException e) {
            //LOG.error("write error {}", e.getMessage());
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
        } catch(Exception e) {
            //LOG.error("read error {}", e.getMessage());
            return null;
        }
        return new String(body);
    }

    public static void close(Socket socket) {
        try {
            socket.close();
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public static void main(String args[]) throws Exception {
        Properties p = new Properties();
        InputStream in = new FileInputStream("conf/delay.server.conf");
        p.load(in);
        //System.out.println("starting ping pong");
        //
        //System.out.println("starting Subscribe Server");
        //
        System.out.println("starting PPS Server");
        DelayServer.class
                .getMethod(p.getProperty("PPSERVER_START_METHOD"), String.class, Integer.class)
                .invoke(DelayServer.class, p.getProperty("PPSSERVER"), Integer.parseInt(p.getProperty("PPSSERVER_PORT")));

        //System.out.println("starting WriteFirst Server");

//        startPingPong("172.19.16.17", 9999);
//        startSubscribe("172.19.16.17", 8888);
//        startPPS("172.19.16.17", 7777);
//        startWF("172.19.16.17", 6666);}
        Thread.sleep(100);
    }

    public static void startPingPong(String host, int port) {
        new Thread(() -> {
            try {
                final ServerSocket server = new ServerSocket();
                server.bind(new InetSocketAddress(host, port));
                while (true) {
                    Socket socket = server.accept();
                    //oio.setTcpNoDelay(true);
                    PingPongServer pp = new PingPongServer(socket);
                    new Thread(pp).start();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }).start();
    }

    public static void startSubscribe(String host, Integer port) {
        new Thread(() -> {
            try {
                final ServerSocket server = new ServerSocket();
                server.bind(new InetSocketAddress(host, port));
                while (true) {
                    Socket socket = server.accept();
                    //oio.setTcpNoDelay(true);
                    SubscribeServer ss = new SubscribeServer(socket);
                    new Thread(ss).start();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }).start();

    }

    public static void startPPS(String host, Integer port) {
        new Thread(() -> {
            try {
                final ServerSocket server = new ServerSocket();
                server.bind(new InetSocketAddress(host, port));
                while (true) {
                    Socket socket = server.accept();
                    //oio.setTcpNoDelay(true);
                    PPSServer pps = new PPSServer(socket);
                    new Thread(pps).start();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }).start();

    }

    public static void startWF(String host, int port) {
        new Thread(() -> {
            try {
                final ServerSocket server = new ServerSocket();
                server.bind(new InetSocketAddress(host, port));
                while(true) {
                    Socket socket = server.accept();
                    //oio.setTcpNoDelay(true);
                    WriteFirst wf = new WriteFirst(socket);
                    new Thread(wf).start();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }).start();
    }

    private static class PingPongServer implements Runnable {

        private Socket socket;

        private PingPongServer(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            InputStream reader ;
            OutputStream writer ;
            try {
                reader = socket.getInputStream();
                writer = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            while(true) {
                String msg = read(reader);
                if(msg != null) {
                    try {
                        Thread.sleep(30);
                    } catch(InterruptedException ie) {
                        ie.printStackTrace();
                    }
                    boolean f = write(writer, "response a message from PingPongServer");
                    if (!f)
                        msg = null;
                }
                if(msg == null) {
                    close(socket);
                    break;
                }
            }

        }
    }

    private static class SubscribeServer implements Runnable {

        private Socket socket;

        private SubscribeServer(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            InputStream reader ;
            OutputStream writer ;
            try {
                reader = socket.getInputStream();
                writer = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            while(true) {
                String msg = read(reader);
                try {
                    Thread.sleep(30);
                } catch(Exception e) {
                    e.printStackTrace();
                }
                if(msg == null) {
                    close(socket);
                    break;
                }
            }

        }
    }

    private static class PPSServer implements Runnable {

        private Socket socket;
        private int count=0;

        private PPSServer(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            InputStream reader ;
            OutputStream writer ;
            try {
                reader = socket.getInputStream();
                writer = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            while(true) {
                String msg = read(reader);
                try {
                    Thread.sleep(30);
                } catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
                if(count++ < 30 && msg != null) {
                    boolean f = write(writer, "response a message from pps");
                    if (!f)
                        msg = null;
                }
                if(msg == null) {
                    close(socket);
                    break;
                }
            }

        }
    }

    private static class WriteFirst implements Runnable {

        private Socket socket;

        private WriteFirst(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            InputStream reader ;
            OutputStream writer ;
            try {
                reader = socket.getInputStream();
                writer = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            while(true) {
                write(writer, "write a msg first");
                String msg = read(reader);
                try {
                    Thread.sleep(1);
                } catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
                if(msg == null) {
                    close(socket);
                    break;
                }
            }

        }
    }
}

