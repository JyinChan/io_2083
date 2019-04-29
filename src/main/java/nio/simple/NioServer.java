package nio.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class NioServer {

    private Logger logger = LoggerFactory.getLogger(NioServer.class);

    private ServerSocketChannel serverSocketChannel;
    private Selector selector;

    public void listen(String host, int port) throws Exception {
        serverSocketChannel  = ServerSocketChannel.open();
        ServerSocket socket = serverSocketChannel.socket();
        socket.bind(new InetSocketAddress(host, port));
        serverSocketChannel.configureBlocking(false);
        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, new Acceptor());
        while (!Thread.interrupted()) {

            int ready = selector.select();
            if (ready == 0) continue;

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIt = selectedKeys.iterator();

            while (keyIt.hasNext()) {
                invoke(keyIt.next());
                keyIt.remove();
            }
        }
    }

    private static void invoke(SelectionKey key) {
        Runnable r = (Runnable)key.attachment();
        if(r != null) {
            r.run();
        }
    }

    private class Acceptor implements Runnable {

        public Acceptor() {}

        public void run() {
            SocketChannel clientChannel;
            try {
                clientChannel = serverSocketChannel.accept();
                if(clientChannel != null) {
                    clientChannel.configureBlocking(false);
                    SelectionKey key = clientChannel.register(selector, SelectionKey.OP_READ);
                    key.attach(new Connection(clientChannel, key));
                }
            } catch(IOException e) {
                logger.error("", e);
            }
        }
    }

    private class Connection implements Runnable {

        private final static String ReadHeaderState = "head";
        private final static String ReadBodyState = "body";

        private ByteBuffer header = ByteBuffer.allocate(5);
        private ByteBuffer readBuf = header;
        private String readState = ReadHeaderState;

        private SocketChannel channel;
        private SelectionKey key;

        public Connection(SocketChannel channel, SelectionKey key) {
            this.channel = channel;
            this.key = key;
        }

        @Override
        public void run() {
            try {
                if (key.isReadable()) {
                    String msg = read();
                    System.out.println(msg);
                    write("Hi.");
                }
            } catch (Exception e) {
                try { channel.close(); } catch (IOException ignore) { }
                logger.error("", e);
            }
        }

        private String read() throws Exception {

            int t = channel.read(readBuf);
            if (t == -1) throw new IOException("-1");

            String content = null;

            if(!readBuf.hasRemaining()) {

                content = new String(readBuf.array());

                if(ReadHeaderState.equals(readState)) {
                    int bLen = Integer.parseInt(content);   //NumberFormatException
                    readBuf = ByteBuffer.allocate(bLen);
                    readState = ReadBodyState;
                    header.clear();
                    return read();  //read a completed msg as more as possible in one time
                }
                else {//ReadBodyState
                    readBuf = header;
                    readState = ReadHeaderState;
                }
            }
            return content;
        }

        private void write(String msg) throws IOException {
            ByteBuffer src = ByteBuffer.wrap(String.format("%05d%s", msg.getBytes().length, msg).getBytes());
            channel.write(src); //here we assume all wrote
        }
    }

}
