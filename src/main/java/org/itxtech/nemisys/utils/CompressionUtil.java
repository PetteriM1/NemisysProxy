package org.itxtech.nemisys.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import lombok.experimental.UtilityClass;
import net.md_5.bungee.jni.zlib.BungeeZlib;

import java.util.zip.DataFormatException;
import java.util.zip.Deflater;

/**
 * @author SupremeMortal
 */
@UtilityClass
public class CompressionUtil {

    private static final ThreadLocal<BungeeZlib> zlibInflaterLocal = ThreadLocal.withInitial(() -> {
        BungeeZlib zlib = NativeCodeFactory.zlib.newInstance();
        zlib.init(false, Deflater.DEFAULT_COMPRESSION);
        return zlib;
    });
    private static final ThreadLocal<BungeeZlib> zlibDeflaterLocal = ThreadLocal.withInitial(() -> {
        BungeeZlib zlib = NativeCodeFactory.zlib.newInstance();
        zlib.init(true, Deflater.DEFAULT_COMPRESSION);
        return zlib;
    });

    public static ByteBuf zlibInflate(ByteBuf buffer) throws DataFormatException {
        if (buffer.getByte(0) != 0x78) throw new DataFormatException("No zlib header");
        ByteBuf source = null;
        ByteBuf decompressed = PooledByteBufAllocator.DEFAULT.directBuffer();

        try {
            if (!buffer.isDirect()) {
                ByteBuf temporary = PooledByteBufAllocator.DEFAULT.directBuffer();
                temporary.writeBytes(buffer);
                source = temporary;
            } else {
                source = buffer;
            }

            zlibInflaterLocal.get().process(source, decompressed);
            return decompressed;
        } catch (DataFormatException e) {
            decompressed.release();
            throw e;
        } finally {
            if (source != null && source != buffer) {
                source.release();
            }
        }
    }

    public static ByteBuf zlibDeflate(ByteBuf buffer) throws DataFormatException {
        ByteBuf dest = PooledByteBufAllocator.DEFAULT.directBuffer();
        try {
            zlibDeflate(buffer, dest);
        } catch (DataFormatException e) {
            dest.release();
            throw e;
        }
        return dest;
    }

    public static void zlibDeflate(ByteBuf toCompress, ByteBuf into) throws DataFormatException {
        ByteBuf destination = null;
        ByteBuf source = null;
        try {
            if (!toCompress.isDirect()) {
                source = PooledByteBufAllocator.DEFAULT.directBuffer();
                source.writeBytes(toCompress);
            } else {
                source = toCompress;
            }

            if (!into.isDirect()) {
                destination = PooledByteBufAllocator.DEFAULT.directBuffer();
            } else {
                destination = into;
            }

            zlibDeflaterLocal.get().process(source, destination);

            if (destination != into) {
                into.writeBytes(destination);
            }
        } finally {
            if (source != null && source != toCompress) {
                source.release();
            }
            if (destination != null && destination != into) {
                destination.release();
            }
        }
    }
}
