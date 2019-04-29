package oio.async;

public class Main {

    public static void main(String args[]) throws InterruptedException {
        AsyncClient client = new AsyncClient("localhost", 8819);
        client.start();
        while(true) {
            Msg msg = new Msg("hello world");
            client.asyncSend(msg);
            Thread.sleep(100);
        }

    }
}
