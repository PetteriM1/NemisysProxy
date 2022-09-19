package org.itxtech.nemisys.network.protocol.mcpe;

import com.nukkitx.network.raknet.RakNetReliability;
import org.itxtech.nemisys.Nemisys;
import org.itxtech.nemisys.Server;
import org.itxtech.nemisys.utils.BinaryStream;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class DataPacket extends BinaryStream implements Cloneable {

    public int protocol = Integer.MAX_VALUE;
    public volatile boolean isEncoded = false;
    private int channel = 0;

    public RakNetReliability reliability = RakNetReliability.RELIABLE_ORDERED;

    public abstract byte pid();

    public abstract void decode();

    public abstract void encode();

    @Override
    public void reset() {
        super.reset();
        if (Nemisys.DEBUG > 1 && protocol == Integer.MAX_VALUE) {
            Server.getInstance().getLogger().debug("Warning: DataPacket#reset() was called before setting the protocol. To ensure multiversion compatibility make sure the protocol is set before trying to encode a packet.", new Throwable());
        }
        this.putUnsignedVarInt(this.pid() & 0xff);
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
