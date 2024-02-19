package org.itxtech.nemisys.network.synlib;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import org.itxtech.nemisys.network.SynapseInterface;

import java.util.List;

/**
 * SynapsePacketDecoder
 * ===============
 * @author boybook
 * Nemisys Project
 * ===============
 */
public class SynapsePacketDecoder extends ReplayingDecoder<SynapsePacketDecoder.State> {

    private final SynapseProtocolHeader header = new SynapseProtocolHeader();

    public SynapsePacketDecoder() {
        super(State.HEADER_MAGIC);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state()) {
            case HEADER_MAGIC:
                if (SynapseProtocolHeader.MAGIC != in.readShort()) {
                    throw new SynapseContextException("Magic value does not match");
                }
                checkpoint(State.HEADER_ID);
            case HEADER_ID:
                header.pid(in.readByte());
                checkpoint(State.HEADER_BODY_LENGTH);
            case HEADER_BODY_LENGTH:
                header.bodyLength(in.readInt());
                checkpoint(State.BODY);
            case BODY:
                int bodyLength = header.bodyLength();
                if (bodyLength < 6291456) {
                    byte[] bytes = new byte[bodyLength];
                    in.readBytes(bytes);
                    out.add(SynapseInterface.getPacket((byte) header.pid(), bytes));
                    break;
                } else {
                    throw new SynapseContextException("Body of request is bigger than limit value 5242880");
                }
            default:
                break;
        }
        checkpoint(State.HEADER_MAGIC);
    }

    enum State {
        HEADER_MAGIC, HEADER_ID, HEADER_BODY_LENGTH, BODY
    }
}
