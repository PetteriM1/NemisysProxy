package org.itxtech.nemisys.network.protocol.mcpe;

public class NetworkSettingsPacket extends DataPacket {

    public int compressionThreshold;
    public PacketCompressionAlgorithm compressionAlgorithm;
    public boolean clientThrottleEnabled;
    public byte clientThrottleThreshold;
    public float clientThrottleScalar;

    @Override
    public byte pid() {
        return ProtocolInfo.NETWORK_SETTINGS_PACKET;
    }

    @Override
    public void encode() {
        this.reset();
        this.putLShort(this.compressionThreshold);
        this.putLShort(this.compressionAlgorithm.ordinal());
        if (protocol >= 554) {
            this.putBoolean(this.clientThrottleEnabled);
            this.putByte(this.clientThrottleThreshold);
            this.putLFloat(this.clientThrottleScalar);
        }
    }

    @Override
    public void decode() {
    }

    public enum PacketCompressionAlgorithm {
        ZLIB,
        SNAPPY
    }
}
