package keepalive;

public class HeartBeat {

    public static HeartBeat buildResponse(String expectRequest, byte[] encodeResponse) {
        return new HeartBeat(false, null, expectRequest, encodeResponse, null, 15000);
    }

    public static HeartBeat buildRequest(byte[] encodeRequest, String expectResponse) {
        return new HeartBeat(true, encodeRequest, null, null, expectResponse, 15000);
    }

    static HeartBeat buildResponse(String response, long ttl) {
        return new HeartBeat(false, null, null, null, response, ttl);
    }

    static HeartBeat buildRequest(String request, long ttl) {
        return new HeartBeat(true, null, request, null, null, ttl);
    }

    static HeartBeat buildException(HeartBeatException heartBeatException) {
        HeartBeat heartBeat = new HeartBeat();
        heartBeat.heartBeatException = heartBeatException;
        return heartBeat;
    }

    private boolean requestPacket;  //if true indicates that it is a heartbeat request.
    private byte[] encodeRequest;
    private String request;
    private String response;
    private byte[] encodeResponse;
    private long ttl; //(millis)time to live, heartbeat interval about ttl/3.

    private HeartBeatException heartBeatException;

    private HeartBeat() {}

    private HeartBeat(boolean requestPacket, byte[] encodeRequest, String request, byte[] encodeResponse, String response, long ttl) {
        this.requestPacket = requestPacket;
        this.encodeRequest = encodeRequest;
        this.request = request;
        this.response = response;
        this.encodeResponse = encodeResponse;
        this.ttl = ttl;
    }

    public HeartBeat withTTL(long ttl) {
        this.ttl = ttl;
        return this;
    }

    boolean isRequestPacket() {
        return requestPacket;
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

    public byte[] getEncodeResponse() {
        return encodeResponse;
    }

    public long getTTL() {
        return ttl;
    }

    public HeartBeatException getHeartBeatException() {
        return heartBeatException;
    }
}
