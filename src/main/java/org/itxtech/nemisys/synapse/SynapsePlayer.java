package org.itxtech.nemisys.synapse;

import org.itxtech.nemisys.Player;
import org.itxtech.nemisys.event.server.DataPacketSendEvent;
import org.itxtech.nemisys.event.synapse.player.SynapsePlayerConnectEvent;
import org.itxtech.nemisys.network.SourceInterface;
import org.itxtech.nemisys.network.protocol.mcpe.DataPacket;
import org.itxtech.nemisys.network.protocol.spp.PlayerLoginPacket;
import org.itxtech.nemisys.network.protocol.spp.TransferPacket;
import org.itxtech.nemisys.utils.ClientData;

import java.net.InetSocketAddress;
import java.util.UUID;

public class SynapsePlayer extends Player {

    protected SynapseEntry synapseEntry;

    public SynapsePlayer(SourceInterface interfaz, SynapseEntry synapseEntry, long clientID, InetSocketAddress socketAddress) {
        super(interfaz, clientID, socketAddress);
        this.synapseEntry = synapseEntry;
    }

    public void handleLoginPacket(PlayerLoginPacket packet) {
        boolean isFirstTimeLogin = packet.isFirstTime;
        SynapsePlayerConnectEvent ev;
        this.getServer().getPluginManager().callEvent(ev = new SynapsePlayerConnectEvent(this, isFirstTimeLogin));
        if (!ev.isCancelled()) {
            DataPacket pk = this.getSynapseEntry().getSynapse().getPacket(packet.cachedLoginPacket);
            pk.setOffset(3);
            pk.decode();
            this.handleDataPacket(pk);
        }
    }

    public SynapseEntry getSynapseEntry() {
        return synapseEntry;
    }

    public void transfer(String hash) {
        ClientData clients = this.getSynapseEntry().getClientData();
        if (clients.clientList.containsKey(hash)) {
            TransferPacket pk = new TransferPacket();
            pk.uuid = this.getUuid();
            pk.clientHash = hash;
            this.getSynapseEntry().sendDataPacket(pk);
        }
    }

    public void setUniqueId(UUID uuid) {
        this.uuid = uuid;
    }
}
