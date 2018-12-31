package org.itxtech.nemisys.network.protocol.mcpe;


/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class SetEntityDataPacket extends DataPacket {

    @Override
    public byte pid() {
        return ProtocolInfo.SET_ENTITY_DATA_PACKET;
    }

    public long eid;

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        this.putEntityRuntimeId(this.eid);
    }
}
