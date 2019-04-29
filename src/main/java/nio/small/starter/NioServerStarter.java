package nio.small.starter;

import nio.small.ConnectionInitializer;
import nio.small.core.Acceptor;
import nio.small.core.IoConnection;
import nio.small.core.Processor;
import nio.small.core.ProcessorPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class NioServerStarter {

    private final static Logger logger = LoggerFactory.getLogger(NioServerStarter.class);

    private ProcessorPool pool;

    private ConnectionInitializer initializer;

    private AtomicBoolean start = new AtomicBoolean(false);

    public NioServerStarter(int allProcessor) {
        if(allProcessor < 1)
            throw new IllegalArgumentException();
        pool = new ProcessorPool(allProcessor);
    }

    public void bind(String host, int port) throws IOException {
        if(!start.compareAndSet(false, true)) {
            throw new IllegalStateException("starter has been started!!!");
        }
        if(initializer == null) {
            throw new IllegalStateException("please config a initializer before");
        }
        ServerSocketChannel serverChannel;
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(host, port));
            serverChannel.configureBlocking(false);
        } catch (IOException ioe) {
            start.set(false);
            throw ioe;
        }
        Processor processor = pool.nextProcessor();
        Acceptor acceptor = new Acceptor(processor, serverChannel, initializer);
        processor.applyRegister(acceptor);
    }

    public NioServerStarter config(ConnectionInitializer initializer) {
        this.initializer = initializer;
        return this;
    }

    public static void main(String args[]) throws Exception {
        NioServerStarter start = new NioServerStarter(1);
        start.config((config) -> {
            config.configDecoder(null)
                    .configEncoder(null)
                    .configHandler(null)
                    .configMaxNumOfWrite(6)
                    .interestedIdleStatus(IoConnection.DEFAULT_IDLE_STATUS)
                    .configIdleInterval(10000)
                    .configPingTimeout(10000)
                    .configPingRequest("")
                    .configPingResponse("")
                    .configReadBufferCapacity(1024);
        }).bind("127.0.0.1", 9999);
    }
}
