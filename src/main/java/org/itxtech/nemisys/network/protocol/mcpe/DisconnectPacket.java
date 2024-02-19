package org.itxtech.nemisys.network.protocol.mcpe;

/**
 * Created by on 15-10-12.
 */
public class DisconnectPacket extends DataPacket {

    public boolean hideDisconnectionScreen;
    public String message;
    public String filteredMessage = "";

    @Override
    public byte pid() {
        return ProtocolInfo.DISCONNECT_PACKET;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        if (protocol >= 622) { // 1.20.40
            this.putVarInt(0); // Disconnect fail reason UNKNOWN
        }
        this.putBoolean(this.hideDisconnectionScreen);
        if (!this.hideDisconnectionScreen) {
            this.putString(this.message);
            if (this.protocol >= 712) { // 1.21.20
                this.putString(this.filteredMessage);
            }
        }
    }
}
