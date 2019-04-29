package nio.small.buffer;

public interface ReadableBuf {

    int readableBytes();

    void read(byte[] dst, int offset, int length);
}
