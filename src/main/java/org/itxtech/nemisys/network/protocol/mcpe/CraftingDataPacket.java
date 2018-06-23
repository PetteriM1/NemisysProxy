package org.itxtech.nemisys.network.protocol.mcpe;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Nukkit Project Team
 */
public class CraftingDataPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CRAFTING_DATA_PACKET;

    public static final int ENTRY_SHAPELESS = 0;
    public static final int ENTRY_SHAPED = 1;
    public static final int ENTRY_FURNACE = 2;
    public static final int ENTRY_FURNACE_DATA = 3;
    public static final int ENTRY_ENCHANT_LIST = 4;
    public static final int ENTRY_SHULKER_BOX = 5;

    public List<Object> entries = new ArrayList<>();
    public boolean cleanRecipes;

    @Override
    public DataPacket clean() {
        entries = new ArrayList<>();
        return super.clean();
    }

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

}
