package org.itxtech.nemisys.network.protocol.mcpe;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class RemoveEntityPacket extends DataPacket {

    public long eid;

    @Override
    public byte pid() {
        return ProtocolInfo.REMOVE_ENTITY_PACKET;
    }

    @Override
    public void decode() {
        this.eid = getEntityUniqueId();
    }

    @Override
    public void encode() {
        this.reset();
        this.putEntityUniqueId(this.eid);
    }
}
