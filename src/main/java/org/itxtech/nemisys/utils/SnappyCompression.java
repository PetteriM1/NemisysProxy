package org.itxtech.nemisys.utils;

import org.xerial.snappy.Snappy;

import java.io.IOException;

public class SnappyCompression {

    public static byte[] rawCompress(byte[] data) throws IOException {
        return Snappy.compress(data);
    }

    public static byte[] rawDecompress(byte[] data, int maxSize) throws IOException {
        int size = Snappy.uncompressedLength(data);
        if (size > maxSize && maxSize > 0) {
            throw new IllegalArgumentException("Input is too big");
        }
        byte[] decompressed = new byte[size];
        Snappy.uncompress(data, 0, data.length, decompressed, 0);
        return decompressed;
    }
}
