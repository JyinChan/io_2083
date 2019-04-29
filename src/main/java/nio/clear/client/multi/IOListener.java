package nio.clear.client.multi;

import java.io.IOException;

public interface IOListener {

    void onReadable(ChannelReader reader);

    void onSendSucceed(String writeMsg);

    void onSendFailed(String writeFailedMsg);

    void onClosed(IOException e);

    void onConnected(AsyncSender sender);

}
