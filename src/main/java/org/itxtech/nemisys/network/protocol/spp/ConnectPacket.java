package org.itxtech.nemisys.network.protocol.spp;

/**
 * Created by boybook on 16/6/24.
 */
public class ConnectPacket extends SynapseDataPacket {

    public int protocol = SynapseInfo.CURRENT_PROTOCOL;
    public int maxPlayers;
    public boolean isLobbyServer;
    public boolean transferShutdown;
    public String description;
    public String password;
    public boolean useSnappy;

    @Override
    public byte pid() {
        return SynapseInfo.CONNECT_PACKET;
    }

    @Override
    public void encode() {
        this.reset();
        this.putInt(this.protocol);
        this.putInt(this.maxPlayers);
        this.putBoolean(this.isLobbyServer);
        this.putBoolean(this.transferShutdown);
        this.putString(this.description);
        this.putString(this.password);
        this.putBoolean(this.useSnappy);
    }

    @Override
    public void decode() {
        this.protocol = this.getInt();
        this.maxPlayers = this.getInt();
        this.isLobbyServer = this.getBoolean();
        this.transferShutdown = getBoolean();
        this.description = this.getString();
        this.password = this.getString();
        this.useSnappy = this.getBoolean();
    }
}
