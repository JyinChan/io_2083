package keepalive;

public interface HeartBeatTransport {

    void send(byte[] hbBytes);

    void receive(HeartBeatFilter hbFilter);

    void destroy();
}
