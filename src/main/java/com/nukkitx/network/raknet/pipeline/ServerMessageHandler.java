package com.nukkitx.network.raknet.pipeline;

import com.nukkitx.network.raknet.RakNetServer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.itxtech.nemisys.Server;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

@ChannelHandler.Sharable
public class ServerMessageHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    public static final String NAME = "rak-server-message-handler";
    private final RakNetServer server;

    public ServerMessageHandler(RakNetServer server) {
        this.server = server;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        InetAddress address = packet.sender().getAddress();

        if (this.server.isBlocked(address)) {
            // Drop incoming traffic from blocked address
            return;
        }

        Integer pps = this.server.packetsPerSecond.get(address);
        if (pps == null) pps = 0;
        pps++;
        if (pps > Server.packetLimit) {
            Server.getInstance().getLogger().warning("Too many packets per second from " + address);
            this.server.block(address, 120, TimeUnit.SECONDS);
            return;
        }
        this.server.packetsPerSecond.put(address, pps);

        ByteBuf buffer = packet.content();
        if (!buffer.isReadable()) {
            return;
        }

        ctx.fireChannelRead(packet.retain());
    }
}
