package org.itxtech.nemisys.network.synlib;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.itxtech.nemisys.network.protocol.spp.SynapseDataPacket;

/**
 * SynapsePacketEncoder
 * ===============
 * @author boybook
 * Nemisys Project
 * ===============
 */
public class SynapsePacketEncoder extends MessageToByteEncoder<SynapseDataPacket> {

    @Override
    protected void encode(ChannelHandlerContext ctx, SynapseDataPacket packet, ByteBuf out) throws Exception {
        if (!packet.isEncoded) {
            packet.encode();
            packet.isEncoded = true;
        }
        byte[] body = packet.getBuffer();
        out.writeShort(SynapseProtocolHeader.MAGIC)
                .writeByte(packet.pid())
                .writeInt(body.length)
                .writeBytes(body);
    }
}
