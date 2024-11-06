package org.itxtech.nemisys;

import lombok.Getter;
import lombok.Setter;
import org.itxtech.nemisys.event.client.ClientAuthEvent;
import org.itxtech.nemisys.event.client.ClientConnectEvent;
import org.itxtech.nemisys.event.client.ClientDisconnectEvent;
import org.itxtech.nemisys.event.client.PluginMsgRecvEvent;
import org.itxtech.nemisys.network.SynapseInterface;
import org.itxtech.nemisys.network.protocol.mcpe.BatchPacket;
import org.itxtech.nemisys.network.protocol.mcpe.DataPacket;
import org.itxtech.nemisys.network.protocol.mcpe.GenericPacket;
import org.itxtech.nemisys.network.protocol.mcpe.TextPacket;
import org.itxtech.nemisys.network.protocol.spp.*;
import org.itxtech.nemisys.utils.BinaryStream;
import org.itxtech.nemisys.utils.MainLogger;
import org.itxtech.nemisys.utils.TextFormat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author PeratX
 * Nemisys Project
 */
public class Client {

    @Getter
    private final Server server;

    private final SynapseInterface interfaz;

    // 0x00 = not supported, 0x01 = enabled (backwards compatible), 0x02 = forced
    public byte snappySupport;

    @Getter
    private final String ip;
    @Getter
    private final int port;

    private final Map<UUID, Player> players = new ConcurrentHashMap<>();

    @Getter
    private boolean verified = false;

    @Getter
    private boolean lobbyServer = false;
    @Getter
    private boolean transferOnShutdown = true;

    @Getter
    private int maxPlayers;

    private long lastUpdate;

    @Getter
    @Setter
    private String description;
    @Getter
    private float tps;
    @Getter
    private float load;
    @Getter
    private long upTime;

    public Client(SynapseInterface interfaz, String ip, int port) {
        this.server = interfaz.getServer();
        this.interfaz = interfaz;
        this.ip = ip;
        this.port = port;
        this.lastUpdate = System.currentTimeMillis();
        this.server.getPluginManager().callEvent(new ClientConnectEvent(this));
    }

    public String getHash() {
        return this.ip + ":" + this.port;
    }

    public void onUpdate(int currentTick) {
        if ((System.currentTimeMillis() - this.lastUpdate) >= 30000) {
            this.close("timeout");
        }
    }

    public void handleDataPacket(SynapseDataPacket packet) {
        if (!this.isVerified() && packet.pid() != SynapseInfo.CONNECT_PACKET && packet.pid() != SynapseInfo.DISCONNECT_PACKET) {
            System.out.println("Unverified client " + this.getIp() + ':' + this.getPort() + " sent " + packet.pid());
            return;
        }
        switch (packet.pid()) {
            case SynapseInfo.BROADCAST_PACKET:
                GenericPacket gPacket = new GenericPacket(((BroadcastPacket) packet).payload);
                for (UUID uniqueId : ((BroadcastPacket) packet).entries) {
                    Player player = this.players.get(uniqueId);
                    if (player != null) {
                        player.sendDataPacket(gPacket, ((BroadcastPacket) packet).direct);
                    }
                }
                break;
            case SynapseInfo.HEARTBEAT_PACKET:
                HeartbeatPacket heartbeatPacket = (HeartbeatPacket) packet;
                this.lastUpdate = System.currentTimeMillis();
                this.tps = heartbeatPacket.tps;
                this.load = heartbeatPacket.load;
                this.upTime = heartbeatPacket.upTime;

                InformationPacket pk = new InformationPacket();
                pk.type = InformationPacket.TYPE_CLIENT_DATA;
                pk.message = this.server.getClientDataJson();
                this.sendDataPacket(pk);
                break;
            case SynapseInfo.CONNECT_PACKET:
                ConnectPacket connectPacket = (ConnectPacket) packet;
                if (connectPacket.protocol != SynapseInfo.CURRENT_PROTOCOL) {
                    this.close("Incompatible SynapseAPI version!", true, org.itxtech.nemisys.network.protocol.spp.DisconnectPacket.TYPE_WRONG_PROTOCOL);
                    return;
                }
                if (this.isVerified()) {
                    if (Nemisys.DEBUG > 1) {
                        this.server.getLogger().debug("Client " + this.getIp() + ':' + this.getPort() + " is already connected");
                    }
                    return;
                }
                pk = new InformationPacket();
                pk.type = InformationPacket.TYPE_LOGIN;
                if (this.server.comparePassword(connectPacket.password)) {
                    this.setVerified();
                    pk.message = InformationPacket.INFO_LOGIN_SUCCESS;
                    this.lobbyServer = connectPacket.isLobbyServer;
                    this.transferOnShutdown = connectPacket.transferShutdown;
                    this.snappySupport = connectPacket.snappySupport;
                    this.description = connectPacket.description;
                    this.maxPlayers = connectPacket.maxPlayers;
                    this.server.addClient(this);
                    this.server.getLogger().notice("Client " + this.getIp() + ':' + this.getPort() + " has connected successfully");
                    this.server.getLogger().notice("lobbyServer: " + this.lobbyServer);
                    this.server.getLogger().notice("transferOnShutdown: " + this.transferOnShutdown);
                    this.server.getLogger().notice("snappySupport: 0x0" + this.snappySupport);
                    this.server.getLogger().notice("description: " + this.description);
                    this.server.getLogger().notice("maxPlayers: " + this.maxPlayers);
                    this.server.updateClientData();
                    this.sendDataPacket(pk);
                } else {
                    pk.message = InformationPacket.INFO_LOGIN_FAILED;
                    this.server.getLogger().emergency("Client " + this.getIp() + ':' + this.getPort() + " tried to connect with wrong password!");
                    this.sendDataPacket(pk);
                    this.close("authentication failed");
                    return;
                }
                this.server.getPluginManager().callEvent(new ClientAuthEvent(this, connectPacket.password));
                break;
            case SynapseInfo.DISCONNECT_PACKET:
                this.close(((DisconnectPacket) packet).message, false);
                break;
            case SynapseInfo.REDIRECT_PACKET:
                UUID uuid = ((RedirectPacket) packet).uuid;

                Player pl = players.get(uuid);
                if (pl != null) {
                    byte[] buffer = ((RedirectPacket) packet).mcpeBuffer;
                    if (buffer.length == 0) {
                        server.getLogger().warning("Redirect packet with buffer length 0");
                        return;
                    }
                    DataPacket send;
                    if (buffer[0] == (byte) 0xfe) {
                        send = new BatchPacket(buffer, 1);
                    } else {
                        send = server.getNetwork().getPacketOrEmpty(buffer[0]);
                        send.setBuffer(buffer, 1);
                    }

                    send.protocol = pl.protocol;
                    send.decode();
                    send.isEncoded = true;
                    pl.addIncomingPacket(send);
                }
                break;
            case SynapseInfo.TRANSFER_PACKET:
                UUID uuid0 = ((TransferPacket) packet).uuid;
                pl = players.get(uuid0);
                if (pl == null) {
                    break;
                }

                String hash = ((TransferPacket) packet).clientHash;
                if (hash.equals("lobby") && !server.getLobbyClients().isEmpty()) {
                    List<String> clnts = new ArrayList<>(server.getLobbyClients().keySet());
                    hash = clnts.get(ThreadLocalRandom.current().nextInt(clnts.size()));
                }

                Map<String, Client> clients = this.server.getClients();
                Client lobby = clients.get(hash);
                if (lobby != null) {
                    this.players.get(uuid0).transfer(lobby);
                }
                break;
            case SynapseInfo.PLAYER_COUNT_PACKET:
                Map<String, Integer> map = ((PlayerCountPacket) packet).data;
                Server.playerCountData.put(this.getDescription(), map.get(this.getDescription()));

                PlayerCountPacket playerCountPacket = new PlayerCountPacket();
                playerCountPacket.data = Server.playerCountData;
                this.sendDataPacket(playerCountPacket);
                break;
            case SynapseInfo.PLUGIN_MESSAGE_PACKET:
                PluginMessagePacket messagePacket = (PluginMessagePacket) packet;
                BinaryStream inputStream = new BinaryStream(messagePacket.data);
                String channel = messagePacket.channel;

                PluginMsgRecvEvent ev = new PluginMsgRecvEvent(this, channel, messagePacket.data.clone());
                this.server.getPluginManager().callEvent(ev);

                if (ev.isCancelled()) {
                    break;
                }

                if (channel.equals("Nemisys")) {
                    try {
                        String subChannel = inputStream.getString();
                        BinaryStream outputStream = new BinaryStream();

                        switch (subChannel) {
                            case "TransferToPlayer":
                                String player = inputStream.getString();
                                String target = inputStream.getString();

                                Player p = this.server.getPlayerExact(player);
                                Player p2 = this.server.getPlayerExact(target);

                                if (p == null || p2 == null) {
                                    break;
                                }

                                p.transfer(p2.getClient());
                                break;
                            case "IP":
                                player = inputStream.getString();

                                p = this.server.getPlayerExact(player);

                                if (p == null) {
                                    break;
                                }

                                outputStream.putString("IP");
                                outputStream.putString(this.server.getIp());
                                outputStream.putInt(this.server.getPort());
                                break;
                            case "PlayerCount":
                                String server = inputStream.getString();

                                Client client = this.server.getClient(this.server.getClientData().getHashByDescription(server));

                                if (client == null) {
                                    break;
                                }

                                outputStream.putString("PlayerCount");
                                outputStream.putString(server);
                                outputStream.putInt(client.getPlayers().size());
                                break;
                            case "GetServers":
                                outputStream.putString("GetServers");

                                List<String> names = new ArrayList<>();
                                this.server.getClients().values().forEach(c -> names.add(c.getDescription()));

                                outputStream.putString(String.join(", ", names));
                                break;
                            case "Message":
                                player = inputStream.getString();
                                String message = inputStream.getString();

                                p = this.server.getPlayerExact(player);

                                if (p == null) {
                                    break;
                                }

                                p.sendMessage(message);
                                break;
                            case "MessageAll":
                                message = inputStream.getString();

                                TextPacket textPacket = new TextPacket();
                                textPacket.type = TextPacket.TYPE_RAW;
                                textPacket.message = message;

                                Server.broadcastPacket(this.server.getOnlinePlayers().values(), textPacket);
                                break;
                            case "UUID":
                                break;
                            case "KickPlayer":
                                player = inputStream.getString();
                                String reason = inputStream.getString();

                                p = this.server.getPlayerExact(player);

                                if (p == null) {
                                    break;
                                }

                                p.close(reason);
                                break;
                        }

                        byte[] data = outputStream.getBuffer();
                        if (data.length > 0) {
                            this.sendPluginMessage(channel, data);
                        }
                    } catch (Exception e) {
                        MainLogger.getLogger().logException(e);
                    }
                }
                break;
            default:
                this.server.getLogger().error("Client " + this.getIp() + ':' + this.getPort() + " sent an unknown packet " + packet.pid());
        }
    }

    public void sendDataPacket(SynapseDataPacket pk) {
        this.interfaz.putPacket(this, pk);
    }

    public void setVerified() {
        this.verified = true;
    }

    public Map<UUID, Player> getPlayers() {
        return this.players;
    }

    public void addPlayer(Player player) {
        this.players.put(player.getUuid(), player);
    }

    public void removePlayer(Player player) {
        removePlayer(player, "generic");
    }

    public void removePlayer(Player player, String reason) {
        this.players.remove(player.getUuid());

        PlayerLogoutPacket pk = new PlayerLogoutPacket();
        pk.reason = reason;
        pk.uuid = player.getUuid();

        this.sendDataPacket(pk);
    }

    public void closeAllPlayers() {
        this.closeAllPlayers("", null);
    }

    public void closeAllPlayers(String reason, Client fallback) {
        for (Player player : this.players.values()) {
            if (fallback == null) {
                player.close(reason);
            } else {
                player.sendMessage(TextFormat.RED + "The server you were previously on went down and you have been connected to lobby");
                player.transfer(fallback);
            }
        }
    }

    public void close() {
        this.close("downstream disconnected");
    }

    public void close(String reason) {
        this.close(reason, true);
    }

    public void close(String reason, boolean needPk) {
        this.close(reason, needPk, DisconnectPacket.TYPE_GENERIC);
    }

    public void close(String reason, boolean needPk, byte type) {
        ClientDisconnectEvent ev;
        this.server.getPluginManager().callEvent(ev = new ClientDisconnectEvent(this, reason, type));
        reason = ev.getReason();
        this.server.getLogger().info("Client " + this.ip + ':' + this.port + " has disconnected due to " + reason);
        if (needPk) {
            DisconnectPacket pk = new DisconnectPacket();
            pk.type = type;
            pk.message = reason;
            this.sendDataPacket(pk);
        }

        this.interfaz.removeClient(this);
        this.server.removeClient(this);

        this.server.updateClientData();

        this.closeAllPlayers(reason, transferOnShutdown ? this.server.getFallbackClient() : null);
    }

    public void sendPluginMessage(String channel, byte[] data) {
        PluginMessagePacket pk = new PluginMessagePacket();
        pk.channel = channel;
        pk.data = data;
        this.sendDataPacket(pk);
    }
}