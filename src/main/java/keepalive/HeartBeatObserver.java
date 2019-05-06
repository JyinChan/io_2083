package keepalive;

public interface HeartBeatObserver {

    /**
     *
     * @param msg received msg
     * @return Whether the msg is a heartbeat
     */
    boolean update(String msg);
}
