package org.itxtech.nemisys.synapse.network.synlib;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.itxtech.nemisys.Nemisys;
import org.itxtech.nemisys.Server;
import org.itxtech.nemisys.network.protocol.spp.SynapseDataPacket;

import java.net.InetSocketAddress;

public class SynapseClientHandler extends ChannelInboundHandlerAdapter {

    private final SynapseClient synapseClient;

    public SynapseClientHandler(SynapseClient synapseClient) {
        this.synapseClient = synapseClient;
    }

    public SynapseClient getSynapseClient() {
        return synapseClient;
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        if (Nemisys.DEBUG > 3) Server.getInstance().getLogger().debug("client-ChannelActive");
        this.getSynapseClient().getSession().channel = ctx.channel();
        InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
        this.getSynapseClient().getSession().updateAddress(address);
        this.getSynapseClient().getSession().setConnected(true);
        this.getSynapseClient().setConnected(true);
        Server.getInstance().getLogger().notice("Synapse Client has connected to " + address.getAddress().getHostAddress() + ':' + address.getPort());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (Nemisys.DEBUG > 3) Server.getInstance().getLogger().debug("client-ChannelInactive");
        this.getSynapseClient().setConnected(false);
        this.getSynapseClient().reconnect();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof SynapseDataPacket) {
            SynapseDataPacket packet = (SynapseDataPacket) msg;
            this.getSynapseClient().pushThreadToMainPacket(packet);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof Exception) Server.getInstance().getLogger().logException((Exception) cause);
        ctx.close();
        this.getSynapseClient().setConnected(false);
    }
}
