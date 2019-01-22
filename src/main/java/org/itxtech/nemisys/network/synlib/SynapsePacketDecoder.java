package org.itxtech.nemisys.network.synlib;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import org.itxtech.nemisys.network.SynapseInterface;
import org.itxtech.nemisys.utils.MainLogger;

import java.util.List;

/**
 * SynapsePacketDecoder
 * ===============
 * author: boybook
 * Nemisys Project
 * ===============
 */
public class SynapsePacketDecoder extends ReplayingDecoder<SynapsePacketDecoder.State> {

    private static final int MAX_BODY_SIZE = 1024 * 1024 * 5;

    private final SynapseProtocolHeader header = new SynapseProtocolHeader();

    public SynapsePacketDecoder() {
        super(State.HEADER_MAGIC);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state()) {
            case HEADER_MAGIC:
                if (checkMagic(in.readShort())) {
                    checkpoint(State.HEADER_ID);
                } else {
                    return;
                }
            case HEADER_ID:
                header.pid(in.readByte());
                checkpoint(State.HEADER_BODY_LENGTH);
            case HEADER_BODY_LENGTH:
                header.bodyLength(in.readInt());
                checkpoint(State.BODY);
            case BODY:
                int bodyLength = checkBodyLength(header.bodyLength());
                byte[] bytes = new byte[bodyLength];
                in.readBytes(bytes);
                out.add(SynapseInterface.getPacket((byte) header.pid(), bytes));
                break;
            default:
                break;
        }
        checkpoint(State.HEADER_MAGIC);
    }

    private int checkBodyLength(int bodyLength) throws SynapseContextException {
        if (bodyLength > MAX_BODY_SIZE) {
            throw new SynapseContextException("body of request is bigger than limit value " + MAX_BODY_SIZE);
        }
        return bodyLength;
    }

    private boolean checkMagic(short magic) {
        if (SynapseProtocolHeader.MAGIC != magic) {
            MainLogger.getLogger().debug("Magic value does not match");
            return false;
        }
        return true;
    }

    enum State {
        HEADER_MAGIC, HEADER_ID, HEADER_BODY_LENGTH, BODY
    }
}
