package org.itxtech.nemisys.utils;

import org.itxtech.nemisys.Server;

import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Zlib {

    public static final Zlib INSTANCE = new Zlib();
    private static final ThreadLocal<Inflater> INFLATER = ThreadLocal.withInitial(Inflater::new);
    private static final ThreadLocal<Deflater> DEFLATER = ThreadLocal.withInitial(Deflater::new);
    private static final ThreadLocal<Inflater> INFLATER_RAW = ThreadLocal.withInitial(() -> new Inflater(true));
    private static final ThreadLocal<Deflater> DEFLATER_RAW = ThreadLocal.withInitial(() -> new Deflater(7, true));
    private static final ThreadLocal<byte[]> BUFFER = ThreadLocal.withInitial(() -> new byte[65536]);
    private static final ThreadLocal<FastByteArrayOutputStream> FBAOS = ThreadLocal.withInitial(() -> new FastByteArrayOutputStream(65536));

    public byte[] deflate(byte[] data, int level) throws IOException {
        Deflater deflater = DEFLATER.get();
        deflater.reset();
        deflater.setLevel(data.length < Server.compressionThreshold ? 0 : level);
        deflater.setInput(data);
        deflater.finish();
        FastByteArrayOutputStream bos = FBAOS.get();
        bos.reset();
        byte[] buffer = BUFFER.get();
        while (!deflater.finished()) {
            int i = deflater.deflate(buffer);
            bos.write(buffer, 0, i);
        }
        //Deflater::end is called the time when the process exits.
        return bos.toByteArray();
    }

    public byte[] deflateRaw(byte[] data, int level) throws IOException {
        Deflater deflater = DEFLATER_RAW.get();
        deflater.reset();
        deflater.setLevel(data.length < Server.compressionThreshold ? 0 : level);
        deflater.setInput(data);
        deflater.finish();
        FastByteArrayOutputStream bos = FBAOS.get();
        bos.reset();
        byte[] buffer = BUFFER.get();
        while (!deflater.finished()) {
            int i = deflater.deflate(buffer);
            bos.write(buffer, 0, i);
        }
        //Deflater::end is called the time when the process exits.
        return bos.toByteArray();
    }

    public byte[] inflate(byte[] data, int maxSize) throws IOException {
        Inflater inflater = INFLATER.get();
        inflater.reset();
        inflater.setInput(data);
        inflater.finished();
        FastByteArrayOutputStream bos = FBAOS.get();
        bos.reset();
        byte[] buffer = BUFFER.get();
        try {
            int length = 0;
            while (!inflater.finished()) {
                int i = inflater.inflate(buffer);
                length += i;
                if (maxSize > 0 && length >= maxSize) {
                    throw new IOException("Inflated data exceeds maximum size");
                }
                bos.write(buffer, 0, i);
            }
            return bos.toByteArray();
        } catch (DataFormatException e) {
            throw new IOException("Unable to inflate Zlib stream", e);
        }
    }

    public byte[] inflateRaw(byte[] data, int maxSize) throws IOException {
        Inflater inflater = INFLATER_RAW.get();
        inflater.reset();
        inflater.setInput(data);
        inflater.finished();
        FastByteArrayOutputStream bos = FBAOS.get();
        bos.reset();
        byte[] buffer = BUFFER.get();
        try {
            int length = 0;
            while (!inflater.finished()) {
                int i = inflater.inflate(buffer);
                if (i == 0) {
                    throw new IOException("Could not decompress data");
                }
                length += i;
                if (maxSize > 0 && length >= maxSize) {
                    throw new IOException("Inflated data exceeds maximum size");
                }
                bos.write(buffer, 0, i);
            }
            return bos.toByteArray();
        } catch (DataFormatException e) {
            throw new IOException("Unable to inflate Zlib stream", e);
        }
    }
}
