package ssl;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

public class SSLServer extends Thread {

    public static void main(String args[]) throws Exception {
        //System.setProperty("javax.net.debug", "all");
        sslContext = SSLUtil.createSSLContext();
        listen();
    }

    private static SSLContext sslContext;

    private static String host = "127.0.0.1";
    private static int port = 8801;

    public static void listen() throws IOException {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(host, port));
        while(true) {
            Socket socket = serverSocket.accept();
            new SSLServer(socket).start();
        }
    }

    private Socket socket;
    private SSLEngine sslEngine;

    private int packetBufferSize;
    private int appBufferSize;

    private ByteBuffer appInput;
    private ByteBuffer appOutput;

    private ByteBuffer sslInput;
    private ByteBuffer sslOutput;

    public SSLServer(Socket socket) {
        this.socket = socket;
        sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(false);
        sslEngine.setNeedClientAuth(false);

        packetBufferSize = sslEngine.getSession().getPacketBufferSize();
        appBufferSize = sslEngine.getSession().getApplicationBufferSize();

        appInput = ByteBuffer.allocate(appBufferSize);
        appOutput = ByteBuffer.allocate(0);

        sslInput = ByteBuffer.allocateDirect(packetBufferSize);
        sslOutput = ByteBuffer.allocateDirect(packetBufferSize);
    }

    public void run() {

        try {

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            byte[] data = new byte[2000];

            /*
            unwrap:OK/NEED_TASK, 204/0 bytes
            wrap:OK/NEED_UNWRAP, 0/1478 bytes
            unwrap:OK/NEED_TASK, 976/0 bytes
            unwrap:OK/NEED_TASK, 269/0 bytes
            unwrap:OK/NEED_UNWRAP, 6/0 bytes
            unwrap:OK/NEED_WRAP, 101/0 bytes
            wrap:OK/NEED_WRAP, 0/6 bytes
            wrap:OK/FINISHED, 0/101 bytes
             */
            handshake: while (true) {
                while(true) {
                    int r = in.read(data);
                    if (r == -1) return;
                    //SSLUtil.log("read:" + r);
                    SSLUtil.transfer(sslInput, data, 0, r);
                    SSLUtil.unwrap(sslEngine, sslInput, appInput);
                    if(sslEngine.getHandshakeStatus().equals(SSLEngineResult.HandshakeStatus.NEED_WRAP)) break;
                }

                while (true) {
                    byte[] outputBytes = SSLUtil.wrap(sslEngine, appOutput, sslOutput);
                    out.write(outputBytes);
                    if(sslEngine.getHandshakeStatus().equals(SSLEngineResult.HandshakeStatus.NEED_UNWRAP)) break;
                    //why not FINISHED? SSLEngine.handshakeStatus: NEED_WRAP NEED_UNWRAP NEED_TASK NOT_HANDSHAKING
                    if(sslEngine.getHandshakeStatus().equals(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)) {
                        SSLUtil.log("finished handshake. begin transfer...");
                        break handshake;
                    }
                }

                SSLUtil.log("-----------------------------------------------");

            }

            SSLUtil.log("packetBufferSize:"+packetBufferSize);
            SSLUtil.log("appBufferSize:"+appBufferSize);

            while(true) {
                ByteBuffer appData = ByteBuffer.wrap("hello, I am server".getBytes());
                //ByteBuffer netData = ByteBuffer.allocate(100);
                byte[] outputBytes = SSLUtil.wrap(sslEngine, appData, sslOutput);
                out.write(outputBytes);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
