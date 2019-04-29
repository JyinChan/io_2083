package nio.small;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionMonitor {

    private final static Logger logger = LoggerFactory.getLogger(ExceptionMonitor.class);

    public static void exceptionCaught(Throwable t) {
        logger.error("" ,t);
    }
}
