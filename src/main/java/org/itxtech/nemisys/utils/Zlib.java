package org.itxtech.nemisys.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;


public abstract class Zlib {

    private static final ThreadLocal<Deflater> DEFLATER = ThreadLocal.withInitial(Deflater::new);
    private static final ThreadLocal<Inflater> INFLATER_RAW = ThreadLocal.withInitial(() -> new Inflater(true));
    private static final ThreadLocal<Deflater> DEFLATER_RAW = ThreadLocal.withInitial(() -> new Deflater(7, true));
    private static final ThreadLocal<byte[]> BUFFER = ThreadLocal.withInitial(() -> new byte[8192]);

    public static byte[] deflate(byte[] data) throws Exception {
        return deflate(data, Deflater.DEFAULT_COMPRESSION);
    }

    public static byte[] deflate(byte[] data, int level) throws Exception {
        Deflater deflater = DEFLATER.get();
        deflater.reset();
        deflater.setLevel(level);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        byte[] buf = BUFFER.get();
        try {
            while (!deflater.finished()) {
                int i = deflater.deflate(buf);
                bos.write(buf, 0, i);
            }
        } finally {
            deflater.end();
        }
        return bos.toByteArray();
    }

    public static byte[] deflateRaw(byte[] data, int level) throws Exception {
        Deflater deflater = DEFLATER_RAW.get();
        deflater.reset();
        deflater.setLevel(level);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        byte[] buf = BUFFER.get();
        try {
            while (!deflater.finished()) {
                int i = deflater.deflate(buf);
                bos.write(buf, 0, i);
            }
        } finally {
            deflater.end();
        }
        return bos.toByteArray();
    }

    public static byte[] inflate(InputStream stream) throws IOException {
        InflaterInputStream inputStream = new InflaterInputStream(stream);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = BUFFER.get();
        int length;

        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }

        buffer = outputStream.toByteArray();
        outputStream.flush();
        outputStream.close();
        inputStream.close();

        return buffer;
    }

    public static byte[] inflateRaw(InputStream stream) throws IOException {
        Inflater inflater = INFLATER_RAW.get();
        inflater.reset();
        InflaterInputStream inputStream = new InflaterInputStream(stream, inflater);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = BUFFER.get();
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
        inputStream.close();

        return buffer;
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
