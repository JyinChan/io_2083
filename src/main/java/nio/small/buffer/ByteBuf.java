package nio.small.buffer;

import java.nio.ByteBuffer;

/**
 * only channel write -> buf <- read by decoder/...
 */
public class ByteBuf implements ReadableBuf, WritableBuf{

    private ByteBuffer buffer;

    private int readerIndex = 0;
    private int writerIndex = 0;

    public ByteBuf(int capacity) {
        buffer = ByteBuffer.allocateDirect(capacity);
    }

    @Override
    public int readableBytes() {
        return writerIndex - readerIndex;
    }

    @Override
    public void read(byte[] dst, int offset, int length) {
        buffer.get(dst, offset, length);
        readerIndex += length;
    }

    //only for inter
    @Override
    public ByteBuffer getBuffer() {
        return buffer;
    }

    //only for inter
    @Override
    public void finishWrite() {
        this.readerIndex = 0;
        //position is a index after write/read
        //it must a writerIndex here
        this.writerIndex = buffer.position();
        //it must a readerIndex before read
        buffer.position(0);
    }

    //only for inter
    //readerIndex and writerIndex are not valid
    @Override
    public void beginWrite() {
        if(readableBytes() > 0) {
            //can assume writerIndex = writerIndex - readerIndex
            buffer.compact();
        } else {
            //do reset
            //can assume writerIndex = readerIndex = 0
            buffer.clear();
        }
    }

    public static void main(String args[]) {
        ReadableBuf buf = new ByteBuf(1024);
        byte[] header = null;
        byte[] body = null;

        if(buf.readableBytes() >= 5) {
            buf.read(header, 0, 5);
            //...init body
            if(buf.readableBytes() > body.length) {
                buf.read(body, 0 ,body.length);
                //finish decode
            } else {
                buf.read(body, 0 ,buf.readableBytes());
            }
        }
    }
}
