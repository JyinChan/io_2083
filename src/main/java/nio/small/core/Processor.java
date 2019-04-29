package nio.small.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Processor implements Runnable {

    private final static Logger logger = LoggerFactory.getLogger(Processor.class);

    private Selector selector;

    private final static long SELECT_TIMEOUT = 1000L;

    private ProcessorPool pool;

    private final Queue<Registrable> registerQueue = new ConcurrentLinkedQueue<>();

    private final Queue<NioConnection> flushQueue = new ConcurrentLinkedQueue<>();

    private final Queue<NioConnection> removeQueue = new ConcurrentLinkedQueue<>();

    Processor(ProcessorPool pool) {
        if(pool == null) {
            throw new IllegalArgumentException("pool is not allowed null");
        }
        try {
            selector = Selector.open();
        } catch (IOException ioe) {
            throw new RuntimeException();
        }
        this.pool = pool;
        this.pool.execute(this);
    }

    @Override
    public void run() {

        try {
            for(;;) {
                int select = selector.select(SELECT_TIMEOUT);
                //maybe do something prevent cpu 100%???
                long currentTime = System.currentTimeMillis();
                if(select > 0) {
                    for(Iterator<SelectionKey> it=selector.selectedKeys().iterator(); it.hasNext(); ) {
                        SelectionKey key = it.next();
                        Processable p = (Processable) key.attachment();
                        p.process(currentTime, false);
                        it.remove();
                    }
                }

                //register connection or acceptor
                doRegister();
                //applyProcess doFlush
                doProcess(currentTime);
                //keep alive
                notifyIdle();
                //unregister
            }
        } catch (IOException ioe) {
            logger.error("", ioe);
        }
    }

    public void applyRegister(Registrable r) {
        registerQueue.add(r);
        selector.wakeup();
    }

    void applyProcess(NioConnection connection) {
        if(connection.setApplyMark(true)) {
            flushQueue.add(connection);
            selector.wakeup();
        }
    }

    private void doRegister() {
        if(registerQueue.isEmpty())
            return;
        for(;;) {
            Registrable r = registerQueue.poll();
            if(r == null)
                break;
            r.register();
        }
    }

    private void doProcess(long currentTime) {
        if(flushQueue.isEmpty())
            return;
        for(;;) {
            NioConnection connection = flushQueue.poll();
            if(connection == null)
                break;
            connection.setApplyMark(false);
            connection.process(currentTime, true);
        }
    }

    private void notifyIdle() {

    }

    Selector getSelector() {
        return selector;
    }

    Processor nextProcessor() {
        return pool.nextProcessor();
    }
}
