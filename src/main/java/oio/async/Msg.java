package oio.async;

public class Msg {

    private String content;
    private String origin;

    public Msg(String content) {
        if(content == null)
            throw new NullPointerException();
        this.content = String.format("%05d%s", content.getBytes().length, content);
        this.origin = content;
    }

    public String getContent() {
        return content;
    }

    public byte[] getBytes() {
        return content.getBytes();
    }

    public String getOrigin() {
        return origin;
    }
}
