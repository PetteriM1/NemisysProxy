package com.nukkitx.network.raknet.pipeline;

import com.nukkitx.network.raknet.RakNetServer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetAddress;

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

        ByteBuf buffer = packet.content();
        if (!buffer.isReadable()) {
            return;
        }

        ctx.fireChannelRead(packet.retain());
    }
}
