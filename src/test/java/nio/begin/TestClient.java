package nio.begin;

public class TestClient {

    public static void main(String args[]) throws Exception {

        NioClient client = new NioClient();
        client.connect("localhost", 8801).start().write("hello world");
        //Thread.sleep(100);
        for(int i=0; i<100; i++)
        client.write("hello world");
        client.stop();
    }
}
