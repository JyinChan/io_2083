package keepalive;

public interface HeartBeatTransport {

    /**
     * send a heartbeat
     * @param hbBytes heartbeat's bytes
     */
    void send(byte[] hbBytes);

    /**
     * if we receive a msg, we must notify it's observer.
     * @see HeartBeatObserver
     * @param observer heartbeat observer
     */
    void addObserver(HeartBeatObserver observer);

    /**
     * it will be invoked if death comes (it means too long without a heartbeat)
     */
    void destroy();
}
