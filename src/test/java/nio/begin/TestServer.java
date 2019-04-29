package nio.begin;

import nio.begin.handler.DefaultServerHandler;
import nio.begin.writer.Writer;

public class TestServer {

    public static void main(String args[]) {
        NioServer server = new NioServer();
        server.build("localhost", 8801)
                .handler(Handler.class)
                .openProcessor(3)
                .start();
    }

    static class Handler extends DefaultServerHandler {

        @Override
        public void handle(String msg, Writer writer) {
            super.handle(msg, writer);
            System.out.println("received:"+msg);
        }
    }
}
