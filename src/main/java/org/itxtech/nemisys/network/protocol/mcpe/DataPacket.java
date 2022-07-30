package org.itxtech.nemisys.network.protocol.mcpe;

import com.nukkitx.network.raknet.RakNetReliability;
import org.itxtech.nemisys.utils.BinaryStream;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class DataPacket extends BinaryStream implements Cloneable {

    public int protocol = -1;
    public volatile boolean isEncoded = false;
    private int channel = 0;

    public RakNetReliability reliability = RakNetReliability.RELIABLE_ORDERED;

    public abstract byte pid();

    public abstract void decode();

    public abstract void encode();

    @Override
    public void reset() {
        super.reset();
        this.putUnsignedVarInt(this.pid());
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }

    @Override
    public DataPacket clone() {
        try {
            return (DataPacket) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    public final void tryEncode() {
        if (!this.isEncoded) {
            this.isEncoded = true;
            this.encode();
        }
    }
}
