package ssl;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.Certificate;

/**
 * keytool -genkey -keyalg RSA -alias selfsigned -keystore keystore.jks -storepass password
 *
 *      client          server          message
 *      ======          ======          =======
 *      wrap()          ...             ClientHello
 *      ...             unwrap()        ClientHello
 *      ...             wrap()          ServerHello/Certificate
 *      unwrap()        ...             ServerHello/Certificate
 *      wrap()          ...             ClientKeyExchange
 *      wrap()          ...             ChangeCipherSpec
 *      wrap()          ...             Finished
 *      ...             unwrap()        ClientKeyExchange
 *      ...             unwrap()        ChangeCipherSpec
 *      ...             unwrap()        Finished
 *      ...             wrap()          ChangeCipherSpec
 *      ...             wrap()          Finished
 *      unwrap()        ...             ChangeCipherSpec
 *      unwrap()        ...             Finished
 */
public class SSLUtil {

    public static void main(String args[]) throws Exception {

        SSLContext sslContext = createSSLContext();
//        SSLEngine sslEngine = sslContext.createSSLEngine();
//        sslEngine.setUseClientMode(true);
//
//        ByteBuffer s = ByteBuffer.allocate(0);
//        ByteBuffer e = ByteBuffer.allocate(20400);
//
//        wrap(sslEngine, s, e);
    }

    private static boolean logging = true;
    private static boolean resultOnce = true;

    public static SSLContext createSSLContext() throws Exception {
        KeyStore ks = KeyStore.getInstance("jks");
        KeyStore ts = KeyStore.getInstance("jks");

        char[] passphrase = "password".toCharArray();
        ks.load(new FileInputStream("keystore.jks"), passphrase);
        ts.load(new FileInputStream("keystore.jks"), passphrase);
        //服务端/客户端证书
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);
        //服务端/客户端信任证书
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        SSLContext sslC = SSLContext.getInstance("TLS");//SSL
        sslC.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sslC;
    }

    public static byte[] wrap(SSLEngine sslEngine, ByteBuffer appData, ByteBuffer netData) throws SSLException {
        SSLEngineResult result = sslEngine.wrap(appData, netData);
        log("wrap:", result);
        checkSSLResult(result, sslEngine);
        byte[] outputBytes = new byte[netData.position()];
        netData.flip();
        netData.get(outputBytes);
        netData.clear();
        return outputBytes;
    }

    public static int transfer(ByteBuffer dst, byte[] src, int offset, int length) {
        int putLen = dst.remaining() >= length ? length : dst.remaining();
        dst.put(src, offset, putLen);
        return putLen;
    }

    /*
    返回产生数据量
     */
    public static int unwrap(SSLEngine sslEngine, ByteBuffer netData, ByteBuffer appData) throws SSLException {

        int bytesProduced = 0;

        netData.flip();

        while (netData.hasRemaining()) {
            SSLEngineResult unwrapResult = sslEngine.unwrap(netData, appData);
            log("unwrap:", unwrapResult);
            //BUFFER_UNDERFLOW
            //1、数据不足以unwrap
            //2、remaining=0(刚好unwrap)
            //3、大小不足 这种情况可以扩大缓冲
            if (unwrapResult.bytesConsumed() == 0) {
                //compact step: 1、copy剩余 2、position=剩余长度 3、limit=capacity
                netData.compact();
                return bytesProduced;
            }
            checkSSLResult(unwrapResult, sslEngine);
            bytesProduced += unwrapResult.bytesProduced();
        }
        //重置
        netData.clear();
        return bytesProduced;
    }

    private static void checkSSLResult(SSLEngineResult result, SSLEngine sslEngine) throws SSLException {
        if(result.getStatus().equals(SSLEngineResult.Status.BUFFER_OVERFLOW)) {
            //如果需要，可以扩大缓冲大小
            throw new SSLException("appData(unwrap)/netData(wrap) not enough");
        }
        if(result.getStatus().equals(SSLEngineResult.Status.CLOSED)) {
            throw new SSLException("SSLEngine Closed");
        }
        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = sslEngine.getDelegatedTask()) != null) {
                log("\trunning delegated task...");
                runnable.run();
            }
            SSLEngineResult.HandshakeStatus hsStatus = sslEngine.getHandshakeStatus();
            if (hsStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                throw new SSLException("handshake shouldn't need additional tasks");
            }
            log("\tnew HandshakeStatus: " + hsStatus);
        }
    }

    private static void log(String str, SSLEngineResult result) {
        if (!logging) {
            return;
        }
        if (resultOnce) {
            resultOnce = false;
            System.out.println("The format of the SSLEngineResult is: \n"
                    + "\t\"getStatus() / getHandshakeStatus()\" +\n"
                    + "\t\"bytesConsumed() / bytesProduced()\"\n");
        }
        SSLEngineResult.HandshakeStatus hsStatus = result.getHandshakeStatus();
        log(str + result.getStatus() + "/" + hsStatus + ", "
                + result.bytesConsumed() + "/" + result.bytesProduced()
                + " bytes");
        if (hsStatus == SSLEngineResult.HandshakeStatus.FINISHED) {
            log("\t...ready for application data");
        }
    }

    public static void log(String str) {
        if (logging) {
            System.out.println(str);
        }
    }
}
