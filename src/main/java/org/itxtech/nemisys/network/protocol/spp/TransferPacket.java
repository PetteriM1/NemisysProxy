package org.itxtech.nemisys.network.protocol.spp;

import java.util.UUID;

/**
 * Created by boybook on 16/6/24.
 */
public class TransferPacket extends SynapseDataPacket {

    public UUID uuid;
    public String clientHash;

    @Override
    public byte pid() {
        return SynapseInfo.TRANSFER_PACKET;
    }

    @Override
    public void encode() {
        this.reset();
        this.putUUID(this.uuid);
        this.putString(this.clientHash);
    }

    @Override
    public void decode() {
        this.uuid = this.getUUID();
        this.clientHash = this.getString();
    }
}
