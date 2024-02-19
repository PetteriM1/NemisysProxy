package org.itxtech.nemisys.network.protocol.mcpe;

/**
 * @author PeratX
 * Nemisys Project
 */
public class GenericPacket extends DataPacket {

    public byte pid;

    public GenericPacket() {
        super();
    }

    public GenericPacket(byte[] buffer) {
        super(buffer);
    }

    public GenericPacket(byte[] buffer, int offset) {
        super(buffer, offset);
    }

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
