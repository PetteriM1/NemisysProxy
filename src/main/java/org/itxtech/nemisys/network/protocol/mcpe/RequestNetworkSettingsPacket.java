package org.itxtech.nemisys.network.protocol.mcpe;

public class RequestNetworkSettingsPacket extends DataPacket {

    public int protocolVersion;

    @Override
    public byte pid() {
        return ProtocolInfo.REQUEST_NETWORK_SETTINGS_PACKET;
    }

    @Override
    public void encode() {
    }

    @Override
    public void decode() {
        this.protocolVersion = this.getInt();
    }
}
