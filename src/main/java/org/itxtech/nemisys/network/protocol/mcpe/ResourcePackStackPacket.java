package org.itxtech.nemisys.network.protocol.mcpe;

public class ResourcePackStackPacket extends DataPacket {

    public boolean mustAccept = false;

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        this.putBoolean(this.mustAccept);
    }

    @Override
    public byte pid() {
        return ProtocolInfo.RESOURCE_PACK_STACK_PACKET;
    }
}
