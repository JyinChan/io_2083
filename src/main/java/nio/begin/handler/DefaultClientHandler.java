package nio.begin.handler;

import nio.begin.writer.Writer;

public class DefaultClientHandler implements CompletionHandler {

    @Override
    public void handle(String msg, Writer writer) {
    }

    @Override
    public void handleWriteFailed(String writeFailedMsg) {

    }

    @Override
    public void handleException(Exception e) {

    }
}

