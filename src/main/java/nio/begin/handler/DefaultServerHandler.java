package nio.begin.handler;

import nio.begin.writer.Writer;

public class DefaultServerHandler implements MessageHandler{

    public void handle(String msg, Writer writer) {
    }

    @Override
    public void handleException(Exception e) {

    }
}
