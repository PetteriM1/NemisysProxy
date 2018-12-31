package org.itxtech.nemisys.network.protocol.mcpe;

import java.util.UUID;

/**
 * @author Nukkit Project Team
 */
public class CraftingEventPacket extends DataPacket {

    public static final int TYPE_SHAPELESS = 0;
    public static final int TYPE_SHAPED = 1;
    public static final int TYPE_FURNACE = 2;
    public static final int TYPE_FURNACE_DATA = 3;
    public static final int TYPE_MULTI = 4;
    public static final int TYPE_SHULKER_BOX = 5;

    public int windowId;
    public int type;
    public UUID id;

    @Override
    public void decode() {
        this.windowId = this.getByte();
        this.type = this.getVarInt();
        this.id = this.getUUID();
    }

    @Override
    public void encode() {
    }

    @Override
    public byte pid() {
        return ProtocolInfo.CRAFTING_EVENT_PACKET;
    }
}
