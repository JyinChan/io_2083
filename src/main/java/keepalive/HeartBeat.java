package keepalive;

public class HeartBeat {

    private boolean isActiveType;
    private byte[] encodeRequest;
    private String request;
    private String response;
    private long ttl;

    private HeartBeatException heartBeatException;

    public HeartBeat(HeartBeatException heartBeatException) {
        this.heartBeatException = heartBeatException;
    }

    public HeartBeat(String request, String response, long ttl) {
        this.request = request;
        this.response = response;
        this.ttl = ttl;
        this.isActiveType = false;
    }

    public HeartBeat(byte[] encodeRequest, String request, String response, long ttl) {
        this.encodeRequest = encodeRequest;
        this.request = request;
        this.response = response;
        this.ttl = ttl;
        this.isActiveType = true;
    }

    public boolean isActiveType() {
        return isActiveType;
    }

    public byte[] getEncodeRequest() {
        return encodeRequest;
    }

    public String getRequest() {
        return request;
    }

    public String getResponse() {
        return response;
    }

    public long getTTL() {
        return ttl;
    }

    public HeartBeatException getHeartBeatException() {
        return heartBeatException;
    }
}
