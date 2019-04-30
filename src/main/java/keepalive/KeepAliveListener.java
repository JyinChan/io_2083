package keepalive;

public interface KeepAliveListener {

    HeartBeat listen() throws InterruptedException;

    void close();
}
