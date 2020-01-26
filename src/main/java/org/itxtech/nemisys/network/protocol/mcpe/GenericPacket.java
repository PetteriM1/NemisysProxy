package org.itxtech.nemisys.network.protocol.mcpe;

/**
 * @author PeratX
 * Nemisys Project
 */
public class GenericPacket extends DataPacket {

    public byte pid;

    @Override
    public byte pid() {
        return pid;
    }

    @Override
    public void encode() {
    }

    @Override
    public void decode() {
    }
}
