package org.itxtech.nemisys.network.protocol.mcpe;

public class EntityPickRequestPacket extends DataPacket {

    @Override
    public byte pid() {
        return ProtocolInfo.ENTITY_PICK_REQUEST_PACKET;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
    }
}
