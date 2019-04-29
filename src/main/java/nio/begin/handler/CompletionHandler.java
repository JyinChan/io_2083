package nio.begin.handler;

public interface CompletionHandler extends MessageHandler {

    void handleWriteFailed(String writeFailedMsg);
}
