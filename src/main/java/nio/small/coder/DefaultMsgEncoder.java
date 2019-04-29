package nio.small.coder;

public class DefaultMsgEncoder implements MsgEncoder {

    @Override
    public String encode(String writeMsg) {
        return writeMsg;
    }
}
