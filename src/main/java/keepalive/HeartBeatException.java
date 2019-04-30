package keepalive;

public class HeartBeatException extends Exception {

    public HeartBeatException(String e) {
        super(e);
    }

    public HeartBeatException(Exception e) {
        super(e);
    }
}
