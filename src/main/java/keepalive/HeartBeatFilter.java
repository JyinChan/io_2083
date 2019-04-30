package keepalive;

public interface HeartBeatFilter {

    boolean filter(String receivedMsg);
}
