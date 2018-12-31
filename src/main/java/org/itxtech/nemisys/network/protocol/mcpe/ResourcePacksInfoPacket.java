package org.itxtech.nemisys.network.protocol.mcpe;

public class ResourcePacksInfoPacket extends DataPacket {

    public boolean mustAccept = false;

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
    }

    @Override
    public byte pid() {
        return ProtocolInfo.RESOURCE_PACKS_INFO_PACKET;
    }
}
