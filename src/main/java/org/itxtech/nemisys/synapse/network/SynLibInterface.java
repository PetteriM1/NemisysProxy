package org.itxtech.nemisys.synapse.network;

import org.itxtech.nemisys.Player;
import org.itxtech.nemisys.network.SourceInterface;
import org.itxtech.nemisys.network.protocol.mcpe.DataPacket;
import org.itxtech.nemisys.network.protocol.spp.RedirectPacket;

/**
 * Created by boybook on 16/6/24.
 */
public class SynLibInterface implements SourceInterface {

    private final SynapseInterface synapseInterface;

    public SynLibInterface(SynapseInterface synapseInterface) {
        this.synapseInterface = synapseInterface;
    }

    @Override
    public void emergencyShutdown() {
    }

    @Override
    public void setName(String name) {

    }

    @Override
    public int getNetworkLatency(Player player) {
        return 0;
    }

    @Override
    public Integer putPacket(Player player, DataPacket packet) {
        return this.putPacket(player, packet, false);
    }

    @Override
    public Integer putPacket(Player player, DataPacket packet, boolean needACK) {
        return this.putPacket(player, packet, needACK, false);
    }

    @Override
    public Integer putPacket(Player player, DataPacket packet, boolean needACK, boolean immediate) {
        if (!player.closed) {
            packet.encode();
            packet.isEncoded = true;
            RedirectPacket pk = new RedirectPacket();
            pk.uuid = player.getUuid();
            pk.direct = immediate;
            pk.mcpeBuffer = packet.getBuffer();
            if (pk.mcpeBuffer.length >= 5242880) {
                player.close("Too big data packet");
            } else {
                this.synapseInterface.putPacket(pk);
            }
        }
        return 0;
    }

    @Override
    public boolean process() {
        return false;
    }

    @Override
    public void close(Player player, String reason) {

    }

    @Override
    public void close(Player player) {

    }

    @Override
    public void shutdown() {

    }
}
