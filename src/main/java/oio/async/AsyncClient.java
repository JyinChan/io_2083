package oio.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncClient extends AbstractAsyncClient {

    private final static Logger logger = LoggerFactory.getLogger(AsyncClient.class);

    //Map<Integer, Future> maybe some request need to wait a response before sending next request;

    public AsyncClient(String host, int ip) {
        super(host, ip);
    }

    @Override
    public void onReceiveMessage(String msg) {
        logger.debug("receive: [{}]", msg);
    }

    @Override
    public void onSendSucceed(String msg) {
        logger.debug("send succeed: [{}]", msg);
    }

    @Override
    public void onSendFailed(String sendFailedMsg, String reason) {
        logger.debug("send failed: [{}]", sendFailedMsg);
    }

    @Override
    public boolean onCheckBeforeSend(Msg msg) {
        return true;
    }

    @Override
    public void doBeforeStop() {
        //
    }
}
