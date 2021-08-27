package org.itxtech.nemisys.network.query;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.itxtech.nemisys.Server;
import org.itxtech.nemisys.event.server.QueryRegenerateEvent;
import org.itxtech.nemisys.utils.Utils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class QueryHandler {

    private static final byte HANDSHAKE = 0x09;
    private static final byte STATISTICS = 0x00;

    private final Server server;
    private byte[] lastToken;
    private byte[] token;
    private byte[] longData;
    private byte[] shortData;
    private long timeout;

    public QueryHandler() {
        this.server = Server.getInstance();
        this.regenerateToken();
        this.lastToken = this.token;
        this.regenerateInfo();
    }

    public static byte[] getTokenString(String token, InetAddress address) {
        return getTokenString(token.getBytes(StandardCharsets.UTF_8), address);
    }

    public static byte[] getTokenString(byte[] token, InetAddress address) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(address.toString().getBytes(StandardCharsets.UTF_8));
            digest.update(token);
            return Arrays.copyOf(digest.digest(), 4);
        } catch (NoSuchAlgorithmException e) {
            return ByteBuffer.allocate(4).putInt(Utils.random.nextInt()).array();
        }
    }

    public void regenerateInfo() {
        QueryRegenerateEvent ev = this.server.getQueryInformation();
        this.longData = ev.getLongQuery();
        this.shortData = ev.getShortQuery();
        this.timeout = System.currentTimeMillis() + ev.getTimeout();
    }

    public void regenerateToken() {
        this.lastToken = this.token;
        byte[] token = new byte[16];
        for (int i = 0; i < 16; i++) {
            token[i] = (byte) Utils.random.nextInt(255);
        }
        this.token = token;
    }

    public void handle(InetSocketAddress address, ByteBuf packet) {
        short packetId = packet.readUnsignedByte();
        int sessionId = packet.readInt();

        switch (packetId) {
            case HANDSHAKE:
                ByteBuf reply = ByteBufAllocator.DEFAULT.ioBuffer(10);
                reply.writeByte(HANDSHAKE);
                reply.writeInt(sessionId);
                reply.writeBytes(getTokenString(this.token, address.getAddress()));
                reply.writeByte(0);

                this.server.getNetwork().sendPacket(address, reply);
                break;
            case STATISTICS:
                byte[] token = new byte[4];
                packet.readBytes(token);

                if (!Arrays.equals(token, getTokenString(this.token, address.getAddress())) &&
                        !Arrays.equals(token, getTokenString(this.lastToken, address.getAddress()))) {
                    break;
                }

                if (this.timeout < System.currentTimeMillis()) {
                    this.regenerateInfo();
                }
                reply = ByteBufAllocator.DEFAULT.ioBuffer(64);
                reply.writeByte(STATISTICS);
                reply.writeInt(sessionId);
                if (packet.readableBytes() == 8) {
                    reply.writeBytes(this.longData);
                } else {
                    reply.writeBytes(this.shortData);
                }

                this.server.getNetwork().sendPacket(address, reply);
                break;
        }
    }
}
