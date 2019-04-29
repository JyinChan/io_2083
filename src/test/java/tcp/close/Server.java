package tcp.close;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    public static void main(String[] args) throws Exception {

        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("localhost", 8888));

        for(; ;) {
            Socket socket = serverSocket.accept();
            new Thread(new Connection(socket)).start();
        }
    }

    private static class Connection implements Runnable {

        private Socket socket;

        Connection(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                test2();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //测试1
        private void test1() throws Exception {
            Thread.sleep(1000);
            //不读数据 直接关闭
            close();
        }

        //测试2-shutdown测试
        private void test2() throws Exception {
            //Thread.sleep(1000);
            shutdownOutput();
            byte[] b = new byte[100];
            try {
                while (true) {
                    int r = socket.getInputStream().read(b);
                    if(r == -1) throw new IOException("-1");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void close() {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void shutdownOutput() {
            try {
                socket.shutdownOutput();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
