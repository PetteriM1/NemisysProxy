package org.itxtech.nemisys;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import org.itxtech.nemisys.command.CommandSender;
import org.itxtech.nemisys.event.TextContainer;
import org.itxtech.nemisys.event.TranslationContainer;
import org.itxtech.nemisys.event.player.*;
import org.itxtech.nemisys.event.server.DataPacketReceiveEvent;
import org.itxtech.nemisys.event.server.DataPacketSendEvent;
import org.itxtech.nemisys.network.SourceInterface;
import org.itxtech.nemisys.network.protocol.mcpe.*;
import org.itxtech.nemisys.network.protocol.mcpe.types.ScoreInfo;
import org.itxtech.nemisys.network.protocol.spp.PlayerLoginPacket;
import org.itxtech.nemisys.network.protocol.spp.RedirectPacket;
import org.itxtech.nemisys.permission.PermissibleBase;
import org.itxtech.nemisys.permission.Permission;
import org.itxtech.nemisys.permission.PermissionAttachment;
import org.itxtech.nemisys.permission.PermissionAttachmentInfo;
import org.itxtech.nemisys.plugin.Plugin;
import org.itxtech.nemisys.scheduler.AsyncTask;
import org.itxtech.nemisys.utils.*;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author PeratX
 * Nemisys Project
 */
public class Player implements CommandSender {

    public boolean closed;
    @Getter
    protected UUID uuid;
    private byte[] cachedLoginPacket = new byte[0];
    @Getter
    private String name;
    @Getter
    private final InetSocketAddress socketAddress;
    @Getter
    private final long clientId;
    @Getter
    private long randomClientId;
    @Getter
    private int protocol = -1;
    public int raknetProtocol;
    @Getter
    private final SourceInterface interfaz;
    @Getter
    private Client client;
    private boolean isFirstTimeLogin = true;
    @Getter
    private ClientChainData loginChainData;

    protected Set<Long> spawnedEntities = new HashSet<>();

    protected final Queue<DataPacket> incomingPackets = new ConcurrentLinkedQueue<>();
    protected final Queue<DataPacket> outgoingPackets = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean ticking = new AtomicBoolean();

    private final PermissibleBase perm;

    protected final Map<String, Set<Long>> scoreboards = new HashMap<>();

    private final Queue<DataPacket> packetQueue = new ConcurrentLinkedDeque<>();

    public Player(SourceInterface interfaz, Long clientID, InetSocketAddress socketAddress) {
        this.interfaz = interfaz;
        this.clientId = clientID;
        this.socketAddress = socketAddress;
        this.name = "";
        this.perm = new PermissibleBase(this);
    }

    public void handleDataPacket(DataPacket packet) {
        try {
            if (this.closed) {
                return;
            }

            if (packet instanceof BatchPacket) {
                this.getServer().getNetwork().processBatch((BatchPacket) packet, this);
                return;
            }

            if (this.getServer().callDataPkEv) {
                DataPacketReceiveEvent ev = new DataPacketReceiveEvent(this, packet);
                this.getServer().getPluginManager().callEvent(ev);
                if (ev.isCancelled()) {
                    return;
                }
            }

            switch (packet.pid()) {
                case ProtocolInfo.LOGIN_PACKET:
                    LoginPacket loginPacket = (LoginPacket) packet;
                    this.cachedLoginPacket = loginPacket.cacheBuffer;
                    this.name = loginPacket.username;
                    this.uuid = loginPacket.clientUUID;
                    if (this.uuid == null) {
                        this.close(TextFormat.RED + "Error");
                        break;
                    }
                    this.randomClientId = loginPacket.clientId;
                    this.protocol = loginPacket.protocol;
                    try {
                        this.loginChainData = ClientChainData.read(loginPacket);
                    } catch (SkinException ex) {
                        this.close("Invalid Skin Data");
                        return;
                    } catch (Exception ex) {
                        getServer().getLogger().logException(ex);
                        this.close("Invalid Client Chain Data");
                        return;
                    }

                    this.getServer().addOnlinePlayer(this.uuid, this);

                    AsyncTask loginTask = new AsyncTask() {
                        final PlayerAsyncPreLoginEvent e = new PlayerAsyncPreLoginEvent(getName(), getUuid(), Player.this.getAddress(), Player.this.getPort());

                        @Override
                        public void onRun() {
                            getServer().getPluginManager().callEvent(e);
                        }

                        @Override
                        public void onCompletion(Server server) {
                            if (closed) {
                                return;
                            }

                            if (e.getLoginResult() != PlayerAsyncPreLoginEvent.LoginResult.SUCCESS) {
                                Player.this.close(e.getKickMessage());
                            } else {
                                Player.this.completeLogin();
                            }
                        }
                    };

                    this.getServer().getScheduler().scheduleAsyncTask(loginTask);

                    return;
                case ProtocolInfo.TEXT_PACKET:
                    TextPacket textPacket = (TextPacket) packet;

                    if (textPacket.type == TextPacket.TYPE_CHAT) {
                        PlayerChatEvent chatEvent = new PlayerChatEvent(this, textPacket.message);
                        getServer().getPluginManager().callEvent(chatEvent);

                        if (chatEvent.isCancelled()) {
                            return;
                        }
                    }
                    break;
            }
        } catch (Throwable t) {
            MainLogger.getLogger().error("Exception happened while handling outgoing packet " + packet.getClass().getSimpleName(), t);
        }

        if (this.client != null) this.redirectPacket(packet.getBuffer());
    }

    protected void handleIncomingPacket(DataPacket pk) {
        if (pk instanceof BatchPacket) {
            processIncomingBatch((BatchPacket) pk);
            return;
        }

        try {
            Long entityId = null;

            switch (pk.pid()) {
                case ProtocolInfo.ADD_PLAYER_PACKET:
                    entityId = ((AddPlayerPacket) pk).entityRuntimeId;
                    break;
                case ProtocolInfo.ADD_ENTITY_PACKET:
                    entityId = ((AddEntityPacket) pk).entityRuntimeId;
                    break;
                case ProtocolInfo.ADD_ITEM_ENTITY_PACKET:
                    entityId = ((AddItemEntityPacket) pk).entityRuntimeId;
                    break;
                case ProtocolInfo.ADD_PAINTING_PACKET:
                    entityId = ((AddPaintingPacket) pk).entityRuntimeId;
                    break;
                case ProtocolInfo.REMOVE_ENTITY_PACKET:
                    spawnedEntities.remove(((RemoveEntityPacket) pk).eid);
                    break;
                case ProtocolInfo.SET_DISPLAY_OBJECTIVE_PACKET:
                    SetDisplayObjectivePacket sdop = (SetDisplayObjectivePacket) pk;
                    scoreboards.putIfAbsent(sdop.objective, new HashSet<>());
                    break;
                case ProtocolInfo.SET_SCORE_PACKET:
                    SetScorePacket ssp = (SetScorePacket) pk;
                    SetScorePacket.Action act = ssp.action;
                    ssp.infos.forEach(info -> scoreboards.compute(info.objective, (k, v) -> {
                        if (v == null) {
                            v = new HashSet<>();
                        }
                        if (act == SetScorePacket.Action.SET) {
                            v.add(info.scoreId);
                        } else {
                            v.remove(info.scoreId);
                        }
                        return v;
                    }));
                    break;
            }

            if (entityId != null) {
                spawnedEntities.add(entityId);
            }
        } catch (Throwable t) {
            MainLogger.getLogger().error("Exception happened while handling incoming packet " + pk.getClass().getSimpleName(), t);
        }

        this.sendDataPacket(pk);
    }

    public void redirectPacket(byte[] buffer) {
        RedirectPacket pk = new RedirectPacket();
        pk.uuid = this.uuid;
        pk.direct = false;
        pk.mcpeBuffer = buffer;
        if (pk.mcpeBuffer.length >= 5240000) {
            this.close("Too big data packet");
        } else {
            this.client.sendDataPacket(pk);
        }
    }

    public void addIncomingPacket(DataPacket pk) {
        this.incomingPackets.offer(pk);
    }

    public void addOutgoingPacket(DataPacket pk) {
        this.outgoingPackets.offer(pk);
    }

    public boolean canTick() {
        return !this.ticking.get();
    }

    public void onUpdate(long currentTick) {
        ticking.set(true);

        while (!outgoingPackets.isEmpty()) {
            handleDataPacket(outgoingPackets.poll());
        }

        while (!incomingPackets.isEmpty()) {
            handleIncomingPacket(incomingPackets.poll());
        }

        if (!this.packetQueue.isEmpty()) {
            List<DataPacket> toBatch = new ArrayList<>();
            DataPacket packet;
            while ((packet = this.packetQueue.poll()) != null) {
                toBatch.add(packet);
            }
            DataPacket[] arr = toBatch.toArray(new DataPacket[0]);
            getServer().batchPackets(this, arr);
        }

        ticking.set(false);
    }

    public void despawnEntities() {
        if (this.spawnedEntities.isEmpty()) {
            return;
        }

        DataPacket[] packets = spawnedEntities.stream().map((id) -> {
            RemoveEntityPacket rpk = new RemoveEntityPacket();
            rpk.eid = id;
            return rpk;
        }).toArray(DataPacket[]::new);
        this.spawnedEntities.clear();

        getServer().batchPackets(this, packets);
    }

    public void removeScoreboards() {
        if (scoreboards.isEmpty()) {
            return;
        }
        Set<String> objectives = this.scoreboards.keySet();
        List<ScoreInfo> infos = new ArrayList<>();
        scoreboards.forEach((obj, inf) -> {
            for (Long scoreId : inf) {
                infos.add(new ScoreInfo(scoreId, obj, 1));
            }
        });
        SetScorePacket ssp = new SetScorePacket();
        ssp.action = SetScorePacket.Action.REMOVE;
        ssp.infos = infos;
        this.sendDataPacket(ssp);
        objectives.forEach(obj -> {
            RemoveObjectivePacket rop = new RemoveObjectivePacket();
            rop.objective = obj;
            this.sendDataPacket(rop);
        });
        scoreboards.clear();
    }

    public void transfer(Client client) {
        PlayerTransferEvent ev;
        this.getServer().getPluginManager().callEvent(ev = new PlayerTransferEvent(this, client));
        if (!ev.isCancelled()) {
            if (this.client != null) {
                this.client.removePlayer(this, "");
                this.despawnEntities();
                this.removeScoreboards();
            }
            this.client = ev.getTargetClient();
            ev.getTargetClient().addPlayer(this);

            PlayerLoginPacket pk = new PlayerLoginPacket();
            pk.raknetProtocol = this.raknetProtocol;
            pk.uuid = this.uuid;
            pk.address = this.getAddress();
            pk.port = this.getPort();
            pk.isFirstTime = this.isFirstTimeLogin;
            pk.cachedLoginPacket = this.cachedLoginPacket;

            this.client.sendDataPacket(pk);

            this.isFirstTimeLogin = false;

            this.getServer().getLogger().info(this.name + " has been transferred to " + this.client.getDescription());
            this.getServer().updateClientData();
        }
    }

    public void sendDataPacket(DataPacket pk) {
        this.sendDataPacket(pk, false);
    }

    public void sendDataPacket(DataPacket pk, boolean direct) {
        if (protocol < 419 || direct || pk.pid() == ProtocolInfo.BATCH_PACKET) {
            this.sendDataPacket(pk, true, false);
        } else {
            this.packetQueue.offer(pk);
        }
    }

    public void sendDataPacket(DataPacket pk, boolean direct, boolean needACK) {
        if (this.getServer().callDataPkEv) {
            DataPacketSendEvent ev = new DataPacketSendEvent(this, pk);
            this.getServer().getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                return;
            }
        }

        this.interfaz.putPacket(this, pk, needACK, direct);
    }

    public int getPing() {
        return this.interfaz.getNetworkLatency(this);
    }

    public void close() {
        this.close("generic reason");
    }

    public void close(String reason) {
        this.close(reason, true);
    }

    public void close(String reason, boolean notify) {
        if (!this.closed) {
            if (notify && reason.length() > 0) {
                DisconnectPacket pk = new DisconnectPacket();
                pk.hideDisconnectionScreen = false;
                pk.message = reason;
                this.sendDataPacket(pk, true, false);
            }

            this.getServer().getPluginManager().callEvent(new PlayerLogoutEvent(this));
            this.closed = true;

            if (this.client != null) {
                try {
                    this.client.removePlayer(this, reason);
                } catch (Exception ignore) {}
            }

            this.getServer().getLogger().info(this.getServer().getLanguage().translateString("{%0}[/{%1}:{%2}] logged out due to {%3}", new String[]{
                    TextFormat.AQUA + this.getName() + TextFormat.WHITE,
                    this.getAddress(),
                    String.valueOf(this.getPort()),
                    this.getServer().getLanguage().translateString(reason)
            }));

            this.interfaz.close(this, notify ? reason : "");
            this.getServer().removePlayer(this);

            try {
                this.cachedLoginPacket = null;
                this.scoreboards.clear();
            } catch (Exception ignore) {}
        }
    }

    protected void processIncomingBatch(BatchPacket packet) {
        byte[] payload;

        try {
            if (this.raknetProtocol >= 10) {
                payload = Zlib.inflateRaw(packet.payload);
                if (payload == null) {
                    this.getServer().getLogger().error("Failed to process incoming batch packet: inflateRaw failed");
                    return;
                }
            } else {
                ByteBuf buf0 = Unpooled.wrappedBuffer(packet.payload);
                ByteBuf buf = CompressionUtil.zlibInflate(buf0);
                buf0.release();
                payload = new byte[buf.readableBytes()];
                buf.readBytes(payload);
                buf.release();
            }
            packet.payload = null;

            BinaryStream buffer = new BinaryStream(payload);

            while (!buffer.feof()) {
                try {
                    byte[] data = buffer.getByteArray();

                    DataPacket pk = getServer().getNetwork().getPacket(data[0]);

                    if (pk != null) {
                        pk.setBuffer(data, 1);
                        pk.decode();
                        pk.isEncoded = true;

                        handleIncomingPacket(pk);
                    }
                } catch (Exception e) {
                    this.getServer().getLogger().warning("Processing incoming batch packet failed!");
                }
            }
        } catch (Exception e) {
            MainLogger.getLogger().logException(e);
        }
    }

    public void sendMessage(String message) {
        TextPacket pk = new TextPacket();
        pk.type = TextPacket.TYPE_RAW;
        pk.message = this.getServer().getLanguage().translateString(message);

        this.sendDataPacket(pk);
    }

    @Override
    public void sendMessage(TextContainer message) {
        if (message instanceof TranslationContainer) {
            this.sendTranslation(message.getText(), ((TranslationContainer) message).getParameters());
            return;
        }

        this.sendMessage(message.getText());
    }

    protected void completeLogin() {
        this.getServer().getLogger().info(this.getServer().getLanguage().translateString("{%0}[/{%1}:{%2}] logged in", new String[]{
                TextFormat.AQUA + this.name + TextFormat.WHITE,
                this.getAddress(),
                String.valueOf(this.getPort())
        }));

        Map<String, Client> c = this.getServer().getMainClients();

        String clientHash;
        if (c.size() > 0) {
            clientHash = new ArrayList<>(c.keySet()).get(Utils.random.nextInt(c.size()));
        } else {
            clientHash = "";
        }

        PlayerLoginEvent ev;
        getServer().getPluginManager().callEvent(ev = new PlayerLoginEvent(this, "plugin reason", clientHash));
        if (ev.isCancelled()) {
            this.close(ev.getKickMessage());
            return;
        }
        if (this.getServer().getMaxPlayers() <= this.getServer().getOnlinePlayers().size()) {
            this.close(TextFormat.RED + "Synapse server is full");
            return;
        }

        Client client = this.getServer().getClient(ev.getClientHash());

        if (client == null) {
            this.close(TextFormat.RED + "All lobby servers are offline");
            return;
        }

        transfer(client);
    }

    public void sendTranslation(String message, String[] parameters) {
        TextPacket pk = new TextPacket();
        pk.type = TextPacket.TYPE_TRANSLATION;
        pk.message = this.getServer().getLanguage().translateString(message, parameters, "nemisys.");
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = this.getServer().getLanguage().translateString(parameters[i], parameters, "nemisys.");
        }
        pk.parameters = parameters;
        this.sendDataPacket(pk);
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    @Override
    public boolean isOp() {
        return false;
    }

    @Override
    public void setOp(boolean value) {
    }

    @Override
    public boolean isPermissionSet(String name) {
        return this.perm.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(Permission permission) {
        return this.perm.isPermissionSet(permission);
    }

    @Override
    public boolean hasPermission(String name) {
        return this.perm != null && this.perm.hasPermission(name);
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return this.perm.hasPermission(permission);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return this.addAttachment(plugin, null);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name) {
        return this.addAttachment(plugin, name, null);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, Boolean value) {
        return this.perm.addAttachment(plugin, name, value);
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        this.perm.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        this.getServer().getPluginManager().unsubscribeFromPermission(Server.BROADCAST_CHANNEL_USERS, this);
        this.getServer().getPluginManager().unsubscribeFromPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);

        if (this.perm == null) {
            return;
        }

        this.perm.recalculatePermissions();

        if (this.hasPermission(Server.BROADCAST_CHANNEL_USERS)) {
            this.getServer().getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_USERS, this);
        }

        if (this.hasPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE)) {
            this.getServer().getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);
        }
    }

    @Override
    public Map<String, PermissionAttachmentInfo> getEffectivePermissions() {
        return this.perm.getEffectivePermissions();
    }

    public String getAddress() {
        return this.socketAddress.getAddress().getHostAddress();
    }

    public int getPort() {
        return this.socketAddress.getPort();
    }

    public InetSocketAddress getSocketAddress() {
        return this.socketAddress;
    }

    public Server getServer() {
        return Server.getInstance();
    }
}
