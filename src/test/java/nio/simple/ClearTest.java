package nio.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
测试clear包
 */
public class ClearTest {

    private static Logger logger = LoggerFactory.getLogger(ClearTest.class);

    public static void main(String args[]) throws Exception {
        class Client extends AbstractNioClient {

            public Client(String host, int port) {
                super(host, port);
            }
            @Override
            void processMsg(String msg) {
                logger.debug("receive [{}]", msg);
                if("hello".equals(msg))
                    write("hi");
            }

            @Override
            void processWriteFailed(String writeFailedMsg) {

            }
        }

        /*
        a problem
        select thread connect 失败
        write thread connect 成功！！！
         */
        int port = 8809;
        for(int i=0; i<1; i++) {
            if(port == 8816) port = 8809;
            Client client = new Client("127.0.0.1", port++);
            new Thread(client, "select"+i).start();
            new Thread(() -> {

                while (true) {
                    client.write("");
//                try {
//                    Thread.sleep(1000);
//                } catch (Exception e) {
//
//                }
                    try {

                        Thread.sleep(/*(int)(Math.random() */ 3000);
                    } catch (Exception e) {

                    }
                }
            }, "write"+i).start();
        }
        Thread.sleep(100);
    }
}
