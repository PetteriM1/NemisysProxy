package org.itxtech.nemisys.network.protocol.mcpe;

/**
 * @author CreeperFace
 */
public class RemoveObjectivePacket extends DataPacket {

    public String objective;

    @Override
    public byte pid() {
        return ProtocolInfo.REMOVE_OBJECTIVE_PACKET;
    }

    @Override
    public void encode() {
        reset();
        putString(objective);
    }

    @Override
    public void decode() {
    }
}
