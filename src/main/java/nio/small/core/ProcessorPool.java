package nio.small.core;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProcessorPool implements Executor {

    private int allProcessor;
    private int currentProcessor;

    private Processor[] processors;

    private ExecutorService es;

    public ProcessorPool(int allProcessor) {
        this.allProcessor = allProcessor;
        this.currentProcessor = 0;
        processors = new Processor[allProcessor];
        es = Executors.newCachedThreadPool();
    }

    public Processor nextProcessor() {
        if(currentProcessor == allProcessor)
            currentProcessor = 0;
        if(processors[currentProcessor] == null) {
            processors[currentProcessor] = new Processor(this);
        }
        return processors[currentProcessor++];
    }

    @Override
    public void execute(Runnable command) {
        es.execute(command);
    }
}
