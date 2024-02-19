package org.itxtech.nemisys.network.protocol.spp;

import java.util.UUID;

/**
 * Created by boybook on 16/6/24.
 */
public class RedirectPacket extends SynapseDataPacket {

    public UUID uuid;
    public boolean direct;
    public byte[] mcpeBuffer;

    @Override
    public byte pid() {
        return SynapseInfo.REDIRECT_PACKET;
    }

    @Override
    public void encode() {
        this.reset();
        this.putUUID(this.uuid);
        this.putBoolean(this.direct);
        this.putUnsignedVarInt(this.mcpeBuffer.length);
        this.put(this.mcpeBuffer);
    }

    @Override
    public void decode() {
        this.uuid = this.getUUID();
        this.direct = this.getBoolean();
        this.mcpeBuffer = this.get((int) this.getUnsignedVarInt());
    }
}
