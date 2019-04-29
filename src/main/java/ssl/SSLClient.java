package ssl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

public class SSLClient {

    public static void main(String args[]) throws Exception {

        SSLContext sslContext = SSLUtil.createSSLContext();

        SSLEngine sslEngine = sslContext.createSSLEngine("client", 80);
        sslEngine.setUseClientMode(true);

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", 8801));

        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();

        int packetBufferSize;
        int appBufferSize;

        ByteBuffer appInput;
        ByteBuffer appOutput;

        ByteBuffer sslInput;
        ByteBuffer sslOutput;

        packetBufferSize = sslEngine.getSession().getPacketBufferSize();
        appBufferSize = sslEngine.getSession().getApplicationBufferSize();

        appInput = ByteBuffer.allocate(appBufferSize);
        appOutput = ByteBuffer.allocate(0);

        sslInput = ByteBuffer.allocateDirect(packetBufferSize);
        sslOutput = ByteBuffer.allocateDirect(packetBufferSize);

        byte[] data = new byte[2000];

        handshake: while(true) {

            while (true) {
                byte[] outputBytes = SSLUtil.wrap(sslEngine, appOutput, sslOutput);
                out.write(outputBytes);
                if(sslEngine.getHandshakeStatus().equals(SSLEngineResult.HandshakeStatus.NEED_UNWRAP)) break;
            }

            while(true) {
                int r = in.read(data);
                if (r == -1) return;
                SSLUtil.log("read:" + r);
                SSLUtil.transfer(sslInput, data, 0, r);
                SSLUtil.unwrap(sslEngine, sslInput, appInput);

                if(sslEngine.getHandshakeStatus().equals(SSLEngineResult.HandshakeStatus.NEED_WRAP)) break;

                if(appInput.position() > 0) SSLUtil.log("receive app data, handshake end...");

                if(sslEngine.getHandshakeStatus().equals(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)) {
                    SSLUtil.log("finished handshake. begin transfer...");
                    break handshake;
                }
            }

            SSLUtil.log("-----------------------------------------------");

        }

        SSLUtil.log("packetBufferSize:"+packetBufferSize);
        SSLUtil.log("appBufferSize:"+appBufferSize);

        //app data
        ByteBuffer appData = ByteBuffer.allocate(100);
        while(true) {
            int r = in.read(data);
            if (r == -1) return;
            SSLUtil.log("read:" + r);
            /*
            may be we should
            while(r > 0) {
                offset = transfer
                r -= offset
                unwrap
            }
             */
            SSLUtil.transfer(sslInput, data, 0, r);
            int p = SSLUtil.unwrap(sslEngine, sslInput, appData);
            if(p > 0) {
                System.out.println("received:" + new String(appData.array()).trim());
                appData.clear();
            }
        }

    }


}
