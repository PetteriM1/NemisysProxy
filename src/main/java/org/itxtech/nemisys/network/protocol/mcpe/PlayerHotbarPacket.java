package org.itxtech.nemisys.network.protocol.mcpe;

public class PlayerHotbarPacket extends DataPacket {

    public int selectedHotbarSlot;

    public int[] slots;

    public boolean selectHotbarSlot = true;

    @Override
    public byte pid() {
        return ProtocolInfo.PLAYER_HOTBAR_PACKET;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
    }
}
