package org.itxtech.nemisys.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;


public abstract class Zlib {

    public static byte[] deflate(byte[] data) throws Exception {
        return deflate(data, Deflater.DEFAULT_COMPRESSION);
    }

    public static byte[] deflate(byte[] data, int level) throws Exception {
        Deflater deflater = new Deflater(level);
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
        bos = null;
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
        bos = null;
        return out;
    }

    public static byte[] inflate(InputStream stream) throws IOException {
        InflaterInputStream inputStream = new InflaterInputStream(stream);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;

        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }

        buffer = outputStream.toByteArray();
        outputStream.flush();
        outputStream.close();
        outputStream = null;
        inputStream.close();
        inputStream = null;
        return buffer;
    }

    public static byte[] inflateRaw(InputStream stream) {
        InflaterInputStream inputStream;
        ByteArrayOutputStream outputStream;
        try {
            inputStream = new InflaterInputStream(stream, new Inflater(true));
            outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;

            while ((length = inputStream.read(buffer)) != -1) {
                if (length == 0) {
                    inputStream.close();
                    throw new IOException("Could not decompress data");
                }
                outputStream.write(buffer, 0, length);
            }

            buffer = outputStream.toByteArray();
            outputStream.flush();
            outputStream.close();
            outputStream = null;
            inputStream.close();
            inputStream = null;
            return buffer;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static byte[] inflate(byte[] data) throws IOException {
        return inflate(new ByteArrayInputStream(data));
    }

    public static byte[] inflateRaw(byte[] data) throws IOException {
        return inflateRaw(new ByteArrayInputStream(data));
    }

    public static byte[] inflate(byte[] data, int maxSize) throws IOException {
        return inflate(new ByteArrayInputStream(data, 0, maxSize));
    }

    public static byte[] inflateRaw(byte[] data, int maxSize) throws IOException {
        return inflateRaw(new ByteArrayInputStream(data, 0, maxSize));
    }
}
