package nio.clear.client.multi;

import java.nio.ByteBuffer;

public class WriteMsg {

    private ByteBuffer content;
    private String origin;

    public WriteMsg(String content) {
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
