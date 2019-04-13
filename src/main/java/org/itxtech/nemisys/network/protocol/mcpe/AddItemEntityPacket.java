package org.itxtech.nemisys.network.protocol.mcpe;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class AddItemEntityPacket extends DataPacket {

    @Override
    public byte pid() {
        return ProtocolInfo.ADD_ITEM_ENTITY_PACKET;
    }

    public long entityUniqueId;
    public long entityRuntimeId;

    @Override
    public void decode() {
        entityUniqueId = getEntityUniqueId();
        entityRuntimeId = getEntityRuntimeId();
    }

    @Override
    public void encode() {
        this.reset();
        this.putEntityUniqueId(this.entityUniqueId);
        this.putEntityRuntimeId(this.entityRuntimeId);
    }
}
