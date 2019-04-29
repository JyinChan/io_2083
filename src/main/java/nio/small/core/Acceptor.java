package nio.small.core;

import nio.small.ConnectionInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Acceptor implements Processable, Registrable {

    private final static Logger logger = LoggerFactory.getLogger(Acceptor.class);

    private Processor processor;
    private ConnectionInitializer initializer;

    private ServerSocketChannel serverChannel;
    private SelectionKey key;

    public Acceptor(Processor processor, ServerSocketChannel serverChannel, ConnectionInitializer initializer) {
        this.processor = processor;
        this.serverChannel = serverChannel;
        this.initializer = initializer;
    }

    @Override
    public void process(long currentTime, boolean isApply) {
        try {
            if (key.isAcceptable()) {
                SocketChannel channel = serverChannel.accept();
                Processor nextProcessor = processor.nextProcessor();
                NioConnection connection = new NioConnection(nextProcessor, channel, initializer);
                nextProcessor.applyRegister(connection);
            }
        } catch (IOException ioe) {
            logger.error("", ioe);
        }
    }

    @Override
    public void register() {
        try {
            Selector selector = processor.getSelector();
            key = serverChannel.register(selector, SelectionKey.OP_ACCEPT, this);
        } catch(IOException ioe) {
            logger.error("", ioe);
        }
    }
}
