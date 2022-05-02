package com.nukkitx.network.raknet.pipeline;

import com.nukkitx.network.raknet.RakNetServer;
import com.nukkitx.network.raknet.RakNetServerSession;
import com.nukkitx.network.raknet.RakNetUtils;
import com.nukkitx.network.util.DisconnectReason;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.itxtech.nemisys.Server;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

import static com.nukkitx.network.raknet.RakNetConstants.*;

@ChannelHandler.Sharable
public class ServerDatagramHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    public static final String NAME = "rak-server-datagram-handler";
    private final RakNetServer server;

    public ServerDatagramHandler(RakNetServer server) {
        this.server = server;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        RakNetServerSession session = this.server.getSession(packet.sender());

        if (session == null) {
            InetAddress address = packet.sender().getAddress();
            Integer pps = this.server.packetsPerSecond.get(address);
            if (pps == null) pps = 0;
            pps++;
            if (pps > Server.packetLimit) {
                Server.getInstance().getLogger().warning("Too many packets per second from " + address);
                this.server.block(address, 120, TimeUnit.SECONDS);
                return;
            }
            this.server.packetsPerSecond.put(address, pps);
        } else {
            int pps = session.pps + 1;
            if (pps > Server.packetLimit) {
                InetAddress address = packet.sender().getAddress();
                Server.getInstance().getLogger().warning("Too many packets per second from " + address);
                this.server.block(address, 120, TimeUnit.SECONDS);
                session.disconnect(DisconnectReason.BAD_PACKET);
                return;
            }
            session.pps = pps;
        }

        ByteBuf buffer = packet.content();
        short packetId = buffer.readByte();

        // These packets don't require a session
        switch (packetId) {
            case ID_UNCONNECTED_PING:
                this.onUnconnectedPing(ctx, packet);
                return;
            case ID_OPEN_CONNECTION_REQUEST_1:
                this.server.onOpenConnectionRequest1(ctx, packet);
                return;
        }

        buffer.readerIndex(0);

        if (session == null) {
            if (this.server.getListener() != null) {
                this.server.getListener().onUnhandledDatagram(ctx, packet);
            }
        } else {
            if (session.getEventLoop().inEventLoop()) {
                session.onDatagram(buffer.retain());
            } else {
                ByteBuf buf = buffer.retain();
                session.getEventLoop().execute(() -> session.onDatagram(buf));
            }
        }
    }

    private void onUnconnectedPing(ChannelHandlerContext ctx, DatagramPacket packet) {
        if (!packet.content().isReadable(24)) {
            return;
        }

        long pingTime = packet.content().readLong();
        if (!RakNetUtils.verifyUnconnectedMagic(packet.content())) {
            return;
        }

        byte[] userData = null;
        if (this.server.getListener() != null) {
            userData = this.server.getListener().onQuery(packet.sender());
        }

        if (userData == null) {
            userData = new byte[0];
        }

        int packetLength = 35 + userData.length;

        ByteBuf buffer = ctx.alloc().ioBuffer(packetLength, packetLength);
        buffer.writeByte(ID_UNCONNECTED_PONG);
        buffer.writeLong(pingTime);
        buffer.writeLong(this.server.getGuid());
        RakNetUtils.writeUnconnectedMagic(buffer);
        buffer.writeShort(userData.length);
        buffer.writeBytes(userData);
        ctx.writeAndFlush(new DatagramPacket(buffer, packet.sender()));
    }
}
