package nio.clear.server;

public interface Processable {

    /**
     * only for inner invoke
     * @param currentTime
     * @param isApply
     */
    void process(long currentTime, boolean isApply);
}
