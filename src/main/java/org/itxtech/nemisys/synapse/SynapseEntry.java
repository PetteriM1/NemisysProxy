package org.itxtech.nemisys.synapse;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import org.itxtech.nemisys.Nemisys;
import org.itxtech.nemisys.Player;
import org.itxtech.nemisys.Server;
import org.itxtech.nemisys.event.synapse.player.SynapsePlayerCreationEvent;
import org.itxtech.nemisys.network.SourceInterface;
import org.itxtech.nemisys.network.protocol.mcpe.DataPacket;
import org.itxtech.nemisys.network.protocol.spp.*;
import org.itxtech.nemisys.synapse.network.SynLibInterface;
import org.itxtech.nemisys.synapse.network.SynapseInterface;
import org.itxtech.nemisys.utils.ClientData;
import org.itxtech.nemisys.utils.Utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Created by boybook on 16/8/21.
 */
public class SynapseEntry {

    private final Synapse synapse;

    private boolean enable;
    private String serverIp;
    private int port;
    private boolean isLobbyServer;
    private String password;
    private SynapseInterface synapseInterface;
    private boolean verified = false;
    private long lastUpdate;
    private final Map<UUID, SynapsePlayer> players = new HashMap<>();
    private SynLibInterface synLibInterface;
    private ClientData clientData;
    private String serverDescription;

    public SynapseEntry(Synapse synapse, String serverIp, int port, boolean isLobbyServer, String password, String serverDescription) {
        this.synapse = synapse;
        this.serverIp = serverIp;
        this.port = port;
        this.isLobbyServer = isLobbyServer;
        this.password = password;
        if (this.password.length() != 16) {
            synapse.getLogger().warning("You must use a 16 bit length password!");
            synapse.getLogger().warning("This SynapseAPI Entry will not be enabled!");
            enable = false;
            return;
        }
        this.serverDescription = serverDescription;

        this.synapseInterface = new SynapseInterface(this, this.serverIp, this.port);
        this.synLibInterface = new SynLibInterface(this.synapseInterface);
        this.lastUpdate = System.currentTimeMillis();
        this.getSynapse().getServer().getScheduler().scheduleRepeatingTask(new Ticker(), 5);
    }

    public Synapse getSynapse() {
        return this.synapse;
    }

    public boolean isEnable() {
        return enable;
    }

    public ClientData getClientData() {
        return clientData;
    }

    public SynapseInterface getSynapseInterface() {
        return synapseInterface;
    }

    public void shutdown() {
        if (this.verified) {
            DisconnectPacket pk = new DisconnectPacket();
            pk.type = DisconnectPacket.TYPE_GENERIC;
            pk.message = "§cProxy server closed";
            this.sendDataPacket(pk);
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
        if (this.synapseInterface != null) this.synapseInterface.shutdown();
    }

    public String getServerDescription() {
        return serverDescription;
    }

    public void setServerDescription(String serverDescription) {
        this.serverDescription = serverDescription;
    }

    public void sendDataPacket(SynapseDataPacket pk) {
        this.synapseInterface.putPacket(pk);
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void broadcastPacket(SynapsePlayer[] players, DataPacket packet) {
        this.broadcastPacket(players, packet, false);
    }

    public void broadcastPacket(SynapsePlayer[] players, DataPacket packet, boolean direct) {
        packet.tryEncode(); //TODO: Set protocol for multiversion compatibility
        BroadcastPacket broadcastPacket = new BroadcastPacket();
        broadcastPacket.direct = direct;
        broadcastPacket.payload = packet.getBuffer();
        broadcastPacket.entries = new ArrayList<>();
        for (SynapsePlayer player : players) {
            broadcastPacket.entries.add(player.getUuid());
        }
        this.sendDataPacket(broadcastPacket);
    }

    public boolean isLobbyServer() {
        return isLobbyServer;
    }

    public void setlobbyServer(boolean lobbyServer) {
        isLobbyServer = lobbyServer;
    }

    public String getHash() {
        return this.serverIp + ':' + this.port;
    }

    public void connect() {
        this.getSynapse().getLogger().notice("Connecting " + this.getHash());
        this.verified = false;
        ConnectPacket pk = new ConnectPacket();
        pk.password = Hashing.md5().hashBytes(this.password.getBytes(StandardCharsets.UTF_8)).toString();
        pk.isLobbyServer = this.isLobbyServer();
        pk.description = this.serverDescription;
        pk.maxPlayers = this.getSynapse().getServer().getMaxPlayers();
        pk.protocol = SynapseInfo.CURRENT_PROTOCOL;
        this.sendDataPacket(pk);
    }

    public void tick() {
        this.synapseInterface.process();
        if (!this.getSynapseInterface().isConnected()) return;
        long time = System.currentTimeMillis();
        if ((time - this.lastUpdate) >= 5000) {
            this.lastUpdate = time;
            HeartbeatPacket pk = new HeartbeatPacket();
            pk.tps = this.getSynapse().getServer().getTicksPerSecondAverage();
            pk.load = this.getSynapse().getServer().getTickUsageAverage();
            pk.upTime = (time - Nemisys.START_TIME) / 1000;
            this.sendDataPacket(pk);
        }

        if (((time - this.lastUpdate) >= 30000) && this.synapseInterface.isConnected()) {
            this.synapseInterface.reconnect();
        }
    }

    public void removePlayer(SynapsePlayer player) {
        UUID uuid = player.getUuid();
        this.players.remove(uuid);
    }

    public void removePlayer(UUID uuid) {
        this.players.remove(uuid);
    }

    private static final Gson GSON = new Gson();

    public void handleDataPacket(SynapseDataPacket pk) {
        switch (pk.pid()) {
            case SynapseInfo.DISCONNECT_PACKET:
                DisconnectPacket disconnectPacket = (DisconnectPacket) pk;
                this.verified = false;
                switch (disconnectPacket.type) {
                    case DisconnectPacket.TYPE_GENERIC:
                        this.getSynapse().getLogger().notice("Synapse Client has disconnected due to " + disconnectPacket.message);
                        this.synapseInterface.reconnect();
                        break;
                    case DisconnectPacket.TYPE_WRONG_PROTOCOL:
                        this.getSynapse().getLogger().error(disconnectPacket.message);
                        break;
                }
                break;
            case SynapseInfo.INFORMATION_PACKET:
                InformationPacket informationPacket = (InformationPacket) pk;
                switch (informationPacket.type) {
                    case InformationPacket.TYPE_LOGIN:
                        if (informationPacket.message.equals(InformationPacket.INFO_LOGIN_SUCCESS)) {
                            this.getSynapse().getLogger().notice("Login success to " + this.serverIp + ':' + this.port);
                            this.verified = true;
                        } else if (informationPacket.message.equals(InformationPacket.INFO_LOGIN_FAILED)) {
                            this.getSynapse().getLogger().notice("Login failed to " + this.serverIp + ':' + this.port);
                        }
                        break;
                    case InformationPacket.TYPE_CLIENT_DATA:
                        this.clientData = GSON.fromJson(informationPacket.message, ClientData.class);
                        break;
                }
                break;
            case SynapseInfo.PLAYER_LOGIN_PACKET:
                PlayerLoginPacket playerLoginPacket = (PlayerLoginPacket) pk;
                SynapsePlayerCreationEvent ev = new SynapsePlayerCreationEvent(this.synLibInterface, SynapsePlayer.class, SynapsePlayer.class, Utils.random.nextLong(), playerLoginPacket.address, playerLoginPacket.port);
                this.getSynapse().getServer().getPluginManager().callEvent(ev);
                Class<? extends SynapsePlayer> clazz = ev.getPlayerClass();
                try {
                    Constructor<? extends SynapsePlayer> constructor = clazz.getConstructor(SourceInterface.class, SynapseEntry.class, long.class, String.class, int.class);
                    SynapsePlayer player = constructor.newInstance(this.synLibInterface, this, ev.getClientId(), ev.getAddress(), ev.getPort());
                    player.networkSettingsUpdated = playerLoginPacket.raknetProtocol >= 11;
                    player.raknetProtocol = playerLoginPacket.raknetProtocol;
                    player.setUniqueId(playerLoginPacket.uuid);
                    this.players.put(playerLoginPacket.uuid, player);
                    this.getSynapse().getServer().addPlayer(player.getSocketAddress(), player);
                    player.handleLoginPacket(playerLoginPacket);
                } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                    Server.getInstance().getLogger().logException(e);
                }
                break;
            case SynapseInfo.REDIRECT_PACKET:
                RedirectPacket redirectPacket = (RedirectPacket) pk;
                UUID uuid = redirectPacket.uuid;
                Player player = this.players.get(uuid);
                if (player != null) {
                    DataPacket pk0 = this.getSynapse().getPacket(redirectPacket.mcpeBuffer);
                    if (pk0 != null) {
                        pk0.protocol = player.protocol;
                        pk0.decode();
                        player.handleDataPacket(pk0);
                    } else {
                        if (player.getClient() != null) {
                            player.redirectPacket(redirectPacket.mcpeBuffer);
                        }
                    }
                }
                break;
            case SynapseInfo.PLAYER_LOGOUT_PACKET:
                PlayerLogoutPacket playerLogoutPacket = (PlayerLogoutPacket) pk;
                Player player1 = this.players.get(playerLogoutPacket.uuid);
                if (player1 != null) {
                    player1.close(playerLogoutPacket.reason, true);
                    this.removePlayer(playerLogoutPacket.uuid);
                }
                break;
        }
    }

    public class Ticker implements Runnable {
        @Override
        public void run() {
            tick();
        }
    }
}
