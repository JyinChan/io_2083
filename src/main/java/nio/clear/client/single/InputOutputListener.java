package nio.clear.client.single;

public interface InputOutputListener {

    void onReceiveMessage(String msg);

    void onSendSucceed(String msg);

    void onIOException(Exception e);

    void onSendFailed(String sendFailedMsg, String reason);

    int onCheckBeforeSend(Msg msg) throws Exception;

}
