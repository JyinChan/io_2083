package oio.async;

import java.io.IOException;

public interface InputOutputListener {

    void onReceiveMessage(String msg);

    void onSendSucceed(String msg);

    void onReconnection(IOException ioe, int oldSocket, ReconnectCallback callBack) ;

    void onException(Exception e);

    void onSendFailed(String sendFailedMsg, String reason);

    boolean onCheckBeforeSend(Msg msg) ;
}
