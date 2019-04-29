package nio.clear.client.single;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncApiCommunicator extends AbstractAsyncClient {

    private Logger logger = LoggerFactory.getLogger(AsyncApiCommunicator.class);

    public AsyncApiCommunicator(String host, int ip) {
        super(host, ip);
    }

    @Override
    public int onCheckBeforeSend(Msg msg) {
        if(msg == null) return IOEventHandler.PEEK;
        return IOEventHandler.ACCESS;
    }

    @Override
    public void doBeforeStop() {

    }

    @Override
    public void onIOException(Exception e) {
        logger.debug("", e);
    }

    @Override
    public void onReceiveMessage(String msg) {

    }

    @Override
    public void onSendSucceed(String msg) {
        logger.debug("send success :{}", msg);
    }

    @Override
    public void onSendFailed(String sendFailedMsg, String reason) {

    }

}
