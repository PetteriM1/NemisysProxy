package org.itxtech.nemisys.network.protocol.mcpe;

public class EntityFallPacket extends DataPacket {

    public long eid;
    public float fallDistance;
    public boolean unknown;

    @Override
    public void decode() {
        this.eid = this.getEntityRuntimeId();
        this.fallDistance = this.getLFloat();
        this.unknown = this.getBoolean();
    }

    @Override
    public void encode() {
    }

    @Override
    public byte pid() {
        return ProtocolInfo.ENTITY_FALL_PACKET;
    }
}
