package org.itxtech.nemisys.network.protocol.mcpe;

public class RiderJumpPacket extends DataPacket {

    public int unknown;

    @Override
    public byte pid() {
        return ProtocolInfo.RIDER_JUMP_PACKET;
    }

    @Override
    public void decode() {
        this.unknown = this.getVarInt();
    }

    @Override
    public void encode() {
        this.reset();
        this.putVarInt(this.unknown);
    }
}
