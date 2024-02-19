package org.itxtech.nemisys.network.protocol.mcpe;

import org.itxtech.nemisys.Nemisys;
import org.itxtech.nemisys.Server;
import org.itxtech.nemisys.utils.BinaryStream;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class DataPacket extends BinaryStream implements Cloneable {

    public int protocol = Integer.MAX_VALUE;
    public volatile boolean isEncoded;

    public abstract byte pid();

    public abstract void decode();

    public abstract void encode();

    public DataPacket() {
        super();
    }

    public DataPacket(byte[] buffer) {
        super(buffer);
    }

    public DataPacket(byte[] buffer, int offset) {
        super(buffer, offset);
    }

    @Override
    public void reset() {
        super.reset();
        if (Nemisys.DEBUG > 1 && protocol == Integer.MAX_VALUE) {
            Server.getInstance().getLogger().debug("Warning: DataPacket#reset() was called before setting the protocol. To ensure multiversion compatibility make sure the protocol is set before trying to encode a packet.", new Throwable());
        }
        this.putUnsignedVarInt(this.pid() & 0xff);
    }

    @Override
    public DataPacket clone() {
        try {
            DataPacket packet = (DataPacket) super.clone();
            packet.setBuffer(this.count < 0 ? null : this.getBuffer());
            packet.offset = this.offset;
            packet.count = this.count;
            return packet;
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
