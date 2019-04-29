package nio.simple;

public class NioServerTest {

    public static void main(String[] args) throws Exception {
        NioServer server = new NioServer();
        server.listen("localhost", 8801);
    }
}
