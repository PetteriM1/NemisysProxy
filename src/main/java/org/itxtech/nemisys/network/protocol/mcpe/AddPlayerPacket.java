package org.itxtech.nemisys.network.protocol.mcpe;

import java.util.UUID;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class AddPlayerPacket extends DataPacket {

    @Override
    public byte pid() {
        return ProtocolInfo.ADD_PLAYER_PACKET;
    }

    public UUID uuid;
    public String username;
    public long entityUniqueId;
    public long entityRuntimeId;

    @Override
    public void decode() {
        uuid = getUUID();
        username = getString();
        entityUniqueId = getEntityUniqueId();
        entityRuntimeId = getEntityRuntimeId();
    }

    @Override
    public void encode() {
    }
}
