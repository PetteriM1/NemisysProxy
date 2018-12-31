package org.itxtech.nemisys.network.protocol.mcpe;

/**
 * @author Nukkit Project Team
 */
public class UpdateAttributesPacket extends DataPacket {

    public long entityId;

    @Override
    public byte pid() {
        return ProtocolInfo.UPDATE_ATTRIBUTES_PACKET;
    }

    public void decode() {
    }

    public void encode() {
        this.reset();

        this.putEntityRuntimeId(this.entityId);
    }
}
