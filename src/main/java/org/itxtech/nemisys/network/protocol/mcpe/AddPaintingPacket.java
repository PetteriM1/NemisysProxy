package org.itxtech.nemisys.network.protocol.mcpe;

/**
 * @author Nukkit Project Team
 */
public class AddPaintingPacket extends DataPacket {

    public long entityRuntimeId;

    @Override
    public void decode() {
        getEntityUniqueId();
        entityRuntimeId = getEntityRuntimeId();
    }

    @Override
    public void encode() {
    }

    @Override
    public byte pid() {
        return ProtocolInfo.ADD_PAINTING_PACKET;
    }
}
