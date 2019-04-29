package io.mapping;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MemoryMappingTestWrite {

    public static long mmapWrite() throws IOException {

        long start = System.currentTimeMillis();

        File file = new File("conf/mmapW.tmp");

        RandomAccessFile raf = new RandomAccessFile(file, "rw");

        FileChannel outChannel = raf.getChannel();

        MappedByteBuffer outputMMB = outChannel.map(FileChannel.MapMode.READ_WRITE, 0, 1000*4096);

        //outputMMB.put((byte)0);
        //outputMMB.put(new byte[1000*4096-1]);
        //outputMMB.put(0, (byte)1);
        //outputMMB.force();
        System.out.println(outputMMB.get(0));

        return System.currentTimeMillis() - start;
    }

    public static long simpleWrite() throws IOException {
        long start = System.currentTimeMillis();
        File file = new File("conf/simpW.tmp");

        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        raf.write(new byte[1000 * 4096]);
        return System.currentTimeMillis() - start;
    }

    public static void main(String args[]) throws Exception {
        System.out.println(mmapWrite());
        //System.out.println(simpleWrite());
    }
}
