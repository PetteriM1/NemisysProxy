package org.itxtech.nemisys.network.protocol.mcpe;

import java.util.UUID;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class AddPlayerPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.ADD_PLAYER_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public UUID uuid;
    public String username;
    public long entityUniqueId;
    public long entityRuntimeId;
    public float x;
    public float y;
    public float z;
    public float speedX;
    public float speedY;
    public float speedZ;
    public float pitch;
    public float yaw;
    public String deviceId = "";

    @Override
    public void decode() {
        uuid = getUUID();
        username = getString();

        getString();
        getVarInt();

        entityUniqueId = getEntityUniqueId();
        entityRuntimeId = getEntityRuntimeId();
    }

    @Override
    public void encode() {
        this.reset();
        this.putUUID(this.uuid);
        this.putString(this.username);
        this.putEntityUniqueId(this.entityUniqueId);
        this.putEntityRuntimeId(this.entityRuntimeId);
        this.putString("");
        this.putVector3f(this.x, this.y, this.z);
        this.putVector3f(this.speedX, this.speedY, this.speedZ);
        this.putLFloat(this.pitch);
        this.putLFloat(this.yaw);
        this.putLFloat(this.yaw);
        this.putVarInt(0);
        this.put(new byte[0]);
        this.putUnsignedVarInt(0);
        this.putUnsignedVarInt(0);
        this.putUnsignedVarInt(0);
        this.putUnsignedVarInt(0);
        this.putUnsignedVarInt(0);
        this.putLLong(entityUniqueId);
        this.putUnsignedVarInt(0);
        this.putString(deviceId);
    }
}
