package io.mapping;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MemoryMappingTestCopy {

    public static void main(String args[]) throws Exception {
        System.out.println(simpleMethod());
        //System.out.println(mmapMethod());
    }

    public static long simpleMethod() throws Exception {
        long start = System.currentTimeMillis();
        FileInputStream in = new FileInputStream("/Users/atpchen/Downloads/smartgit-macosx-18_1_5.dmg");
        FileOutputStream out = new FileOutputStream("conf/smart_simple.dmg");

        byte[] b = new byte[in.available()];
        in.read(b);

        out.write(b);
        return System.currentTimeMillis() - start;
    }

    public static long mmapMethod() throws Exception {
        long start = System.currentTimeMillis();
        FileInputStream in = new FileInputStream("/Users/atpchen/Downloads/smartgit-macosx-18_1_5.dmg");
        FileChannel channel = in.getChannel();

        FileOutputStream out = new FileOutputStream("conf/smart_mmap.dmg");
        FileChannel outChannel = out.getChannel();

        MappedByteBuffer b = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());

        outChannel.write(b);

        return System.currentTimeMillis() - start;
    }


}
