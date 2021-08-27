package org.itxtech.nemisys.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;


public abstract class Zlib {

    public static byte[] deflate(byte[] data, int level) throws Exception {
        Deflater deflater = new Deflater(level, false);
        deflater.setLevel(level);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        byte[] buf = new byte[1024];
        try {
            while (!deflater.finished()) {
                int i = deflater.deflate(buf);
                bos.write(buf, 0, i);
            }
        } finally {
            deflater.end();
        }
        byte[] out = bos.toByteArray();
        bos.close();
        return out;
    }

    public static byte[] deflateRaw(byte[] data, int level) throws Exception {
        Deflater deflater = new Deflater(level, true);
        deflater.setLevel(level);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        byte[] buf = new byte[1024];
        try {
            while (!deflater.finished()) {
                int i = deflater.deflate(buf);
                bos.write(buf, 0, i);
            }
        } finally {
            deflater.end();
        }
        byte[] out = bos.toByteArray();
        bos.close();
        return out;
    }

    public static byte[] inflate(InputStream stream, boolean raw) throws IOException {
        InflaterInputStream inputStream = new InflaterInputStream(stream, new Inflater(raw));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            if (length == 0) {
                inputStream.close();
                throw new IOException("Could not decompress data");
            }
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }

    public static byte[] inflate(byte[] data, int maxSize) throws IOException {
        return inflate(new ByteArrayInputStream(data, 0, maxSize), false);
    }

    public static byte[] inflateRaw(byte[] data, int maxSize) throws IOException {
        return inflate(new ByteArrayInputStream(data, 0, maxSize), true);
    }
}
