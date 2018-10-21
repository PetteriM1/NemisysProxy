package org.itxtech.nemisys.network.protocol.spp;

/**
 * Created by PetteriM1
 */
public class ChatPacket extends SynapseDataPacket {

    public static final int NETWORK_ID = SynapseInfo.CHAT_PACKET;

    public String text;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.text);
    }

    @Override
    public void decode() {
        this.text = this.getString();
    }
}
