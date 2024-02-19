package org.itxtech.nemisys;

import lombok.Getter;
import org.itxtech.nemisys.command.CommandSender;
import org.itxtech.nemisys.event.TextContainer;
import org.itxtech.nemisys.event.TranslationContainer;
import org.itxtech.nemisys.event.player.*;
import org.itxtech.nemisys.event.server.DataPacketReceiveEvent;
import org.itxtech.nemisys.event.server.DataPacketSendEvent;
import org.itxtech.nemisys.network.SourceInterface;
import org.itxtech.nemisys.network.encryption.PrepareEncryptionTask;
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

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author PeratX
 * Nemisys Project
 */
public class Player implements CommandSender {

    public volatile boolean closed;
    private boolean loginVerified;
    private volatile boolean loginPacketReceived;
    private boolean isFirstTimeLogin = true;
    private final int[] receivedPackets = new int[256];
    private int batchPackets;
    private final AtomicBoolean ticking = new AtomicBoolean();
    @Getter
    protected UUID uuid;
    private byte[] cachedLoginPacket;
    @Getter
    private String name = "";
    @Getter
    private final InetSocketAddress socketAddress;
    @Getter
    private final long clientId;
    /**
     * Client protocol version
     */
    public int protocol = Integer.MAX_VALUE;
    /**
     * Client RakNet protocol version
     */
    public int raknetProtocol = Integer.MAX_VALUE;
    /**
     * Whether 1.19.30+ client is ready to receive compressed packets
     */
    public boolean networkSettingsUpdated;
    @Getter
    private final SourceInterface interfaz;
    @Getter
    private Client client;
    @Getter
    private ClientChainData loginChainData;
    private final PermissibleBase perm;
    @Getter
    private final Server server;

    private final Set<Long> spawnedEntities = new HashSet<>();
    private final Map<String, Set<Long>> scoreboards = new HashMap<>();

    private final Queue<DataPacket> synapseIncomingPackets = new ConcurrentLinkedQueue<>();
    private final Queue<DataPacket> synapseOutgoingPackets = new ConcurrentLinkedQueue<>();
    private final Queue<DataPacket> outgoingPacketQueue = new ConcurrentLinkedQueue<>();

    private boolean awaitingEncryptionHandshake;
    @Getter
    private Cipher encryptionCipher;
    @Getter
    private Cipher decryptionCipher;
    @Getter
    private SecretKey encryptionKey;
    @Getter
    private final AtomicLong sentEncryptedPacketCount = new AtomicLong();

    public Player(SourceInterface interfaz, Long clientID, InetSocketAddress socketAddress) {
        this.server = Server.getInstance();
        this.interfaz = interfaz;
        this.clientId = clientID;
        this.socketAddress = socketAddress;
        this.perm = new PermissibleBase(this);
    }

    public void handleDataPacket(DataPacket packet) {
        try {
            if (this.closed) {
                return;
            }

            boolean batch = packet instanceof BatchPacket;
            if (batch) {
                if (++this.batchPackets > Server.batchLimit) {
                    this.getServer().getLogger().warning(this.name + " sent too many batch packets (count=" + this.batchPackets + ", max=" + Server.batchLimit + ")");
                    this.close("Too many packets");
                } else {
                    this.getServer().getNetwork().processBatch((BatchPacket) packet, this);
                }
                return;
            }

            byte pid = packet.pid();
            int index = pid & 0xff;
            int count = receivedPackets[index];
            if (count > Server.packetLimit) {
                this.getServer().getLogger().warning(this.name + " sent too many packets (pid=" + pid + ", index=" + index + ", count=" + count + ", max=" + Server.packetLimit + ")");
                this.close("Too many packets");
                return;
            }
            receivedPackets[index] = count + 1;

            if (!loginVerified && pid != ProtocolInfo.LOGIN_PACKET && pid != ProtocolInfo.REQUEST_NETWORK_SETTINGS_PACKET && pid != ProtocolInfo.CLIENT_TO_SERVER_HANDSHAKE_PACKET) {
                this.getServer().getLogger().warning("Got a data packet before logging in from " + this.getAddress() + " (pid=" + pid + ")");
                this.close("Invalid packet");
                return;
            }

            if (Server.callDataPkReceiveEv) {
                DataPacketReceiveEvent ev = new DataPacketReceiveEvent(this, packet);
                this.getServer().getPluginManager().callEvent(ev);
                if (ev.isCancelled()) {
                    return;
                }
            }

            if (Nemisys.DEBUG > 2) {
                this.server.getLogger().debug("[" + this.protocol + "/" + this.raknetProtocol + "] Inbound " + (this.name.isEmpty() ? this.getAddress() : this.name) + ": " + packet.getClass().getSimpleName() + " (pid=" + pid + ")");
            }

            switch (pid) {
                case ProtocolInfo.REQUEST_NETWORK_SETTINGS_PACKET:
                    if (this.raknetProtocol < 11) {
                        return;
                    }

                    if (this.loginPacketReceived) {
                        this.getServer().getLogger().debug(name + ": got a RequestNetworkSettingsPacket but player is already logged in");
                        return;
                    } else if (this.networkSettingsUpdated) {
                        this.getServer().getLogger().debug(name + ": got a RequestNetworkSettingsPacket but network settings are already updated");
                        return;
                    }

                    RequestNetworkSettingsPacket networkSettingsRequest = (RequestNetworkSettingsPacket) packet;
                    this.protocol = networkSettingsRequest.protocolVersion;
                    NetworkSettingsPacket settingsPacket = new NetworkSettingsPacket();
                    settingsPacket.compressionAlgorithm = Server.useSnappy ? NetworkSettingsPacket.PacketCompressionAlgorithm.SNAPPY : NetworkSettingsPacket.PacketCompressionAlgorithm.ZLIB;
                    settingsPacket.compressionThreshold = 256;
                    this.quickBatch(settingsPacket, true);
                    this.networkSettingsUpdated = true;
                    break;
                case ProtocolInfo.LOGIN_PACKET:
                    if (this.loginPacketReceived) {
                        this.close("Invalid login packet");
                        return;
                    }

                    this.loginPacketReceived = true;

                    LoginPacket loginPacket = (LoginPacket) packet;
                    this.protocol = loginPacket.getProtocol();
                    this.name = loginPacket.username;
                    this.uuid = loginPacket.clientUUID;

                    if (this.name == null || this.uuid == null) {
                        this.close("Invalid login data (identifier)");
                        return;
                    }

                    try {
                        this.loginChainData = ClientChainData.read(loginPacket);
                    } catch (ClientChainData.TooBigSkinException ex) {
                        this.close("disconnectionScreen.invalidSkin");
                        return;
                    } catch (Exception ex) {
                        getServer().getLogger().logException(ex);
                        this.close("Invalid login data (skin?!)");
                        return;
                    }

                    if (!loginChainData.isXboxAuthed()) {
                        this.close("disconnectionScreen.notAuthenticated");
                        return;
                    }

                    this.cachedLoginPacket = loginPacket.cacheBuffer;

                    this.getServer().getScheduler().scheduleAsyncTask(new PrepareEncryptionTask(this) {

                        @Override
                        public void onCompletion(Server server) {
                            if (Player.this.closed) {
                                return;
                            }

                            if (this.getHandshakeJwt() == null || this.getEncryptionKey() == null || this.getEncryptionCipher() == null || this.getDecryptionCipher() == null) {
                                Player.this.close("Failed to enable encryption");
                                return;
                            }

                            ServerToClientHandshakePacket handshakePacket = new ServerToClientHandshakePacket();
                            handshakePacket.jwt = this.getHandshakeJwt();
                            Player.this.sendDataPacket(handshakePacket, true);

                            Player.this.awaitingEncryptionHandshake = true;
                            Player.this.encryptionKey = this.getEncryptionKey();
                            Player.this.encryptionCipher = this.getEncryptionCipher();
                            Player.this.decryptionCipher = this.getDecryptionCipher();
                        }
                    });
                    return;
                case ProtocolInfo.CLIENT_TO_SERVER_HANDSHAKE_PACKET:
                    if (!this.awaitingEncryptionHandshake) {
                        this.close("Invalid encryption handshake");
                        return;
                    }

                    this.awaitingEncryptionHandshake = false;

                    this.processPreLogin();
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

    private void processPreLogin() {
        this.loginVerified = true;
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
                    scoreboards.putIfAbsent(((SetDisplayObjectivePacket) pk).objective, new HashSet<>());
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
        if (buffer.length >= 5242880) {
            if (Nemisys.DEBUG > 1) {
                this.server.getLogger().debug("redirectPacket buffer.length=" + buffer.length + " >= 5242880");
            }
            this.close("Too big data packet");
            return;
        }
        RedirectPacket pk = new RedirectPacket();
        pk.uuid = this.uuid;
        pk.mcpeBuffer = buffer;
        this.client.sendDataPacket(pk);
    }

    public void addIncomingPacket(DataPacket pk) {
        this.synapseIncomingPackets.offer(pk);
    }

    public void addOutgoingPacket(DataPacket pk) {
        this.synapseOutgoingPackets.offer(pk);
    }

    public boolean canTick() {
        return !this.ticking.get();
    }

    public void onUpdate(long currentTick) {
        ticking.set(true);

        // To Synapse downstream
        while (!synapseOutgoingPackets.isEmpty()) {
            handleDataPacket(synapseOutgoingPackets.poll());
        }

        // From Synapse downstream
        while (!synapseIncomingPackets.isEmpty()) {
            handleIncomingPacket(synapseIncomingPackets.poll());
        }

        // To player
        if (currentTick % 5 == 0) { // Queue packets to be batched together
            if (!this.outgoingPacketQueue.isEmpty()) {
                List<DataPacket> toBatch = new ArrayList<>();
                DataPacket packet;
                while ((packet = this.outgoingPacketQueue.poll()) != null) {
                    toBatch.add(packet);
                }
                server.batchPackets(this, toBatch);
            }
        }

        // Clear packet counters
        if (currentTick % 100 == 0) {
            this.batchPackets = 0;
            for (int i = 0; i < 256; i++) {
                receivedPackets[i] = 0;
            }
        }

        ticking.set(false);
    }

    public void despawnEntities() {
        if (this.spawnedEntities.isEmpty()) {
            return;
        }

        for (Long id : this.spawnedEntities) {
            RemoveEntityPacket rpk = new RemoveEntityPacket();
            rpk.eid = id;
            this.sendDataPacket(rpk);
        }

        this.spawnedEntities.clear();
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

            if (ev.getTargetClient().getPlayers().containsKey(this.getUuid())) {
                this.close("Already connected");
                return;
            }

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
        if (Server.callDataPkSendEv) {
            DataPacketSendEvent ev = new DataPacketSendEvent(this, pk);
            this.getServer().getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                return;
            }
        }

        if (Nemisys.DEBUG > 2) {
            this.server.getLogger().debug("[" + this.protocol + "/" + this.raknetProtocol + "] Outbound " + (this.name.isEmpty() ? this.getAddress() : this.name) + ": " + pk.getClass().getSimpleName() + " (pid=" + pk.pid() + ")");
        }

        if (direct || this.protocol < 419 || pk instanceof BatchPacket) {
            this.interfaz.putPacket(this, pk, false, true); // should probably not be immediate
        } else {
            this.outgoingPacketQueue.offer(pk);
        }
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
                this.quickBatch(pk, this.raknetProtocol >= 11 && !this.networkSettingsUpdated);
            }

            this.getServer().getPluginManager().callEvent(new PlayerLogoutEvent(this));
            this.closed = true;

            if (this.client != null) {
                try {
                    this.client.removePlayer(this, reason);
                } catch (Exception ex) {
                    if (Nemisys.DEBUG > 1) {
                        this.server.getLogger().debug("Ignored", ex);
                    }
                }
            }

            this.getServer().getLogger().info(this.getServer().getLanguage().translateString("{%0}[/{%1}:{%2}] logged out due to {%3}", new String[]{
                    TextFormat.AQUA + this.getName() + TextFormat.WHITE,
                    this.getAddress(),
                    String.valueOf(this.getPort()),
                    this.getServer().getLanguage().translateStringI(reason)
            }));

            this.interfaz.close(this, notify ? reason : "");
            this.getServer().removePlayer(this);

            try {
                this.cachedLoginPacket = null;
                this.scoreboards.clear();
            } catch (Exception ex) {
                if (Nemisys.DEBUG > 1) {
                    this.server.getLogger().debug("Ignored", ex);
                }
            }
        }
    }

    protected void processIncomingBatch(BatchPacket packet) {
        try {
            byte[] decompressedPayload;
            if (packet.noCompression || (this.raknetProtocol >= 11 && !this.networkSettingsUpdated)) {
                decompressedPayload = packet.payload;
            } else if (this.raknetProtocol >= 11 && this.client.useSnappy) {
                decompressedPayload = SnappyCompression.rawDecompress(packet.payload, -1);
            } else if (this.raknetProtocol >= 10) {
                decompressedPayload = Zlib.INSTANCE.inflateRaw(packet.payload, -1);
            } else {
                decompressedPayload = Zlib.INSTANCE.inflate(packet.payload, -1);
            }
            BinaryStream buffer = new BinaryStream(decompressedPayload);
            while (!buffer.feof()) {
                try {
                    byte[] data = buffer.getByteArray();
                    if (data.length == 0) {
                        this.getServer().getLogger().warning("Batch buffer empty");
                        this.close("Failed to process incoming packet");
                        return;
                    }
                    DataPacket pk = getServer().getNetwork().getPacketOrEmpty(data[0]);
                    pk.setBuffer(data, 1);
                    pk.protocol = this.protocol;
                    pk.decode();
                    pk.isEncoded = true;
                    handleIncomingPacket(pk);
                } catch (Exception ex) {
                    this.getServer().getLogger().warning("Failed to process incoming packet", ex);
                }
            }
        } catch (Exception ex) {
            this.getServer().getLogger().warning("Failed to process incoming batch packet", ex);
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

        Map<String, Client> c = this.getServer().getLobbyClients();

        String clientHash;
        if (c.isEmpty()) {
            clientHash = "";
        } else {
            clientHash = new ArrayList<>(c.keySet()).get(ThreadLocalRandom.current().nextInt(c.size()));
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

    protected void quickBatch(DataPacket pk, boolean noCompression) {
        pk.protocol = this.protocol;
        if (Server.callDataPkSendEv) {
            DataPacketSendEvent event = new DataPacketSendEvent(this, pk);
            this.server.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
        }
        pk.tryEncode();
        byte[] buf = pk.getBuffer();
        BinaryStream stream = new BinaryStream(5 + buf.length);
        stream.putUnsignedVarInt(buf.length);
        stream.put(buf);
        try {
            byte[] bytes = stream.getBuffer();
            BatchPacket batched = new BatchPacket();
            if (noCompression) {
                batched.payload = bytes;
            } else if (Server.useSnappy && this.raknetProtocol >= 11) {
                batched.payload = SnappyCompression.rawCompress(bytes);
            } else if (this.raknetProtocol >= 10) {
                batched.payload = Zlib.INSTANCE.deflateRaw(bytes, Server.compressionLevel);
            } else {
                batched.payload = Zlib.INSTANCE.deflate(bytes, Server.compressionLevel);
            }
            if (Nemisys.DEBUG > 2) {
                this.server.getLogger().debug("[" + this.protocol + "/" + this.raknetProtocol + "] Outbound " + (this.name.isEmpty() ? this.getAddress() : this.name) + ": " + pk.getClass().getSimpleName() + " (pid=" + pk.pid() + ")");
            }
            this.interfaz.putPacket(this, batched, false, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
