package nio.begin.handler;

import nio.begin.writer.Writer;

public interface MessageHandler extends ExceptionHandler {

    void handle(String msg, Writer writer);

}
