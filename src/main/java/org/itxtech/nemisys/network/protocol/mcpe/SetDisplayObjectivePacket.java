package org.itxtech.nemisys.network.protocol.mcpe;

/**
 * @author CreeperFace
 */
public class SetDisplayObjectivePacket extends DataPacket {

    public String objective;

    @Override
    public byte pid() {
        return ProtocolInfo.SET_DISPLAY_OBJECTIVE_PACKET;
    }

    @Override
    public void encode() {
    }

    @Override
    public void decode() {
        getString();
        objective = getString();
    }
}
