package org.itxtech.nemisys.network.protocol.mcpe;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class BatchPacket extends DataPacket {

    public byte[] payload;
    public boolean noCompression;

    public BatchPacket() {
        super();
    }

    public BatchPacket(byte[] buffer) {
        super(buffer);
    }

    public BatchPacket(byte[] buffer, int offset) {
        super(buffer, offset);
    }

    @Override
    public byte pid() {
        return ProtocolInfo.BATCH_PACKET;
    }

    @Override
    public void decode() {
        this.payload = this.get();
    }

    @Override
    public void encode() {
    }
}
