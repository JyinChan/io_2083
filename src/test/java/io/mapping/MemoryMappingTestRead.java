package io.mapping;

import sun.nio.ch.FileChannelImpl;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MemoryMappingTestRead {

    public static void main(String args[]) throws Exception {
        //System.out.println(mmapMethod());
        //System.out.println(simpleMethod());

    }

    public static long simpleMethod() throws IOException {

        long start = System.currentTimeMillis();

        FileInputStream in = new FileInputStream("/Users/atpchen/Downloads/smartgit-macosx-18_1_5.dmg");

        byte[] buffer = new byte[in.available()];

        in.read(buffer);

        //System.out.println(new String(buffer));
        return System.currentTimeMillis() - start;
    }

    public static long mmapMethod() throws IOException {

        long start = System.currentTimeMillis();

        File file = new File("/Users/atpchen/Downloads/smartgit-macosx-18_1_5.dmg");

        FileInputStream in = new FileInputStream(file);
        FileChannel channel = FileChannelImpl.open(in.getFD(), file.getPath(), true, true, in);

        MappedByteBuffer inputMMB = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());

        byte[] buffer = new byte[(int)channel.size()];
        inputMMB.get(buffer);

        //System.out.println(new String(buffer));

        return System.currentTimeMillis() - start;
    }

}
