package keepalive;

public interface KeepAliveListener {

    /**
     * it will block until we received a heartbeat request/response
     * @return heartbeat request/response
     */
    HeartBeat listen();

    /**
     * it can stop keeping alive.
     */
    void close();
}
