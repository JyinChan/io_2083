package nio.clear.client.single;

import java.nio.ByteBuffer;

public class Msg {

    private ByteBuffer content;
    private String origin;

    public Msg(String content) {
        if(content == null)
            throw new NullPointerException();
        String fs = String.format("%05d%s", content.length(), content);
        this.content = ByteBuffer.wrap(fs.getBytes());
        this.origin = content;
    }
    ByteBuffer getContent() {
        return content;
    }
    public String getOrigin() { return origin; }

}
