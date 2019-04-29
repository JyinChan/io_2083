package nio.simple;

public class NioClientImpl extends AbstractNioClient {

    public NioClientImpl(String host, int ip) {
        super(host, ip);
    }

    @Override
    void processMsg(String msg) {
        System.out.println(msg);
    }

    @Override
    void processWriteFailed(String writeFailedMsg) {
        System.out.println("write failure:"+writeFailedMsg);
    }

    public static void main(String args[]) throws Exception {
        NioClientImpl sri = new NioClientImpl("localhost", 8801);
        sri.start();
        Thread.sleep(1000);
        for(int j=0; j<1; j++) {

            new Thread(() -> {
                for(int i=0 ;i<20; i++) {
                    sri.write("hello world ");
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {

                    }
                }
            }).start();

        }

        Thread.sleep(10);

    }
}
