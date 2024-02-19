package org.itxtech.nemisys.network.protocol.mcpe;

public class ServerToClientHandshakePacket extends DataPacket {

    public String jwt;

    @Override
    public byte pid() {
        return ProtocolInfo.SERVER_TO_CLIENT_HANDSHAKE_PACKET;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.jwt);
    }
}
