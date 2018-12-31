package org.itxtech.nemisys.network.protocol.mcpe;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class MobEquipmentPacket extends DataPacket {

    @Override
    public byte pid() {
        return ProtocolInfo.MOB_EQUIPMENT_PACKET;
    }

    public long eid;
    public int inventorySlot;
    public int hotbarSlot;
    public int windowId;

    @Override
    public void decode() {
        this.eid = this.getEntityRuntimeId();
    }

    @Override
    public void encode() {
        this.reset();
        this.putEntityRuntimeId(this.eid);
    }
}
