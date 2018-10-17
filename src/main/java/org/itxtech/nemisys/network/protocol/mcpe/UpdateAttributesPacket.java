package org.itxtech.nemisys.network.protocol.mcpe;

/**
 * @author Nukkit Project Team
 */
public class UpdateAttributesPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.UPDATE_ATTRIBUTES_PACKET;

    public long entityId;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public void decode() {
    }

    public void encode() {
        this.reset();

        this.putEntityRuntimeId(this.entityId);
    }
}
