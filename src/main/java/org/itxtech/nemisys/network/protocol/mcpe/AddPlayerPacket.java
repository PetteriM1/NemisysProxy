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
    public long entityRuntimeId;

    @Override
    public void decode() {
        uuid = getUUID();
        username = getString();
        if (protocol < 534) { // 1.19.10
            getEntityUniqueId();
        }
        entityRuntimeId = getEntityRuntimeId();
    }

    @Override
    public void encode() {
    }
}
