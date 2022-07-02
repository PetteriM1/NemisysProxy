package org.itxtech.nemisys;

import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.Setter;
import org.itxtech.nemisys.command.*;
import org.itxtech.nemisys.event.HandlerList;
import org.itxtech.nemisys.event.TranslationContainer;
import org.itxtech.nemisys.event.server.QueryRegenerateEvent;
import org.itxtech.nemisys.lang.BaseLang;
import org.itxtech.nemisys.math.NemisysMath;
import org.itxtech.nemisys.network.Network;
import org.itxtech.nemisys.network.RakNetInterface;
import org.itxtech.nemisys.network.SourceInterface;
import org.itxtech.nemisys.network.SynapseInterface;
import org.itxtech.nemisys.network.protocol.mcpe.BatchPacket;
import org.itxtech.nemisys.network.protocol.mcpe.DataPacket;
import org.itxtech.nemisys.network.query.QueryHandler;
import org.itxtech.nemisys.permission.DefaultPermissions;
import org.itxtech.nemisys.plugin.JavaPluginLoader;
import org.itxtech.nemisys.plugin.Plugin;
import org.itxtech.nemisys.plugin.PluginManager;
import org.itxtech.nemisys.scheduler.ServerScheduler;
import org.itxtech.nemisys.synapse.Synapse;
import org.itxtech.nemisys.synapse.SynapseEntry;
import org.itxtech.nemisys.utils.*;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author MagicDroidX &amp; Box
 * Nukkit
 */
public class Server {

    public static final String BROADCAST_CHANNEL_ADMINISTRATIVE = "nemisys.broadcast.admin";
    public static final String BROADCAST_CHANNEL_USERS = "nemisys.broadcast.user";

    private static Server instance = null;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private boolean hasStopped = false;
    private final PluginManager pluginManager;
    private final ServerScheduler scheduler;
    private int tickCounter;
    private long nextTick;
    private final float[] tickAverage = {100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100};
    private final float[] useAverage = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private float maxTick = 100;
    private float maxUse = 0;
    private final MainLogger logger;
    private final CommandReader console;
    private final SimpleCommandMap commandMap;
    private final ConsoleCommandSender consoleSender;
    private final int maxPlayers;
    private final int port;
    private final String ip;
    private final String motd;
    private final Network network;
    private final BaseLang baseLang;
    private final String filePath;
    private final String dataPath;
    private final String pluginPath;
    private QueryHandler queryHandler;
    private QueryRegenerateEvent queryRegenerateEvent;
    private final Config properties;
    private final Map<InetSocketAddress, Player> players = new ConcurrentHashMap<>();
    private final Map<UUID, Player> playersUUIDs = new ConcurrentHashMap<>();
    private final SynapseInterface synapseInterface;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private ClientData clientData = new ClientData();
    private String clientDataJson = "";
    private final Map<String, Client> mainServers = new ConcurrentHashMap<>();
    private Synapse synapse;
    private final int compressionLevel;
    public static int dataLimit;
    public static int packetLimit = 1000;
    static boolean handleChat;
    static boolean callDataPkSendEv;
    static boolean callDataPkReceiveEv;
    public final boolean plusOnePlayerCount;
    public static boolean enableQuery;
    private final String queryVersion;
    @SuppressWarnings("unused")
    public int uptime = 0;
    public final static Map<String, Integer> playerCountData = new ConcurrentHashMap<>();
    private final Thread currentThread;
    private final String synapsePassword;

    @Getter
    @Setter
    private int playersPerThread;

    private static final Gson GSON = new Gson();

    private final ThreadPoolExecutor playerTicker = new ThreadPoolExecutor(1, Runtime.getRuntime().availableProcessors(),
            1, TimeUnit.MINUTES, new LinkedBlockingQueue<>(), new ThreadFactoryBuilder().setNameFormat("Player Ticker - #%d").setDaemon(true).build());

    public Server(MainLogger logger, final String filePath, String dataPath, String pluginPath) {
        instance = this;
        currentThread = Thread.currentThread();

        this.logger = logger;
        this.filePath = filePath;

        if (!new File(pluginPath).exists()) {
            new File(pluginPath).mkdirs();
        }

        this.dataPath = new File(dataPath).getAbsolutePath() + '/';
        this.pluginPath = new File(pluginPath).getAbsolutePath() + '/';

        this.console = new CommandReader();
        this.console.start();

        this.properties = new Config(this.dataPath + "server.properties", Config.PROPERTIES, new ServerProperties());

        if (!this.getPropertyBoolean("ansi", true)) Nemisys.ANSI = false;

        this.baseLang = new BaseLang("eng");

        Object poolSize = this.getConfig("async-workers", "auto");
        if (!(poolSize instanceof Integer)) {
            try {
                poolSize = Integer.valueOf((String) poolSize);
            } catch (Exception e) {
                poolSize = Math.max(Runtime.getRuntime().availableProcessors() + 1, 4);
            }
        }

        this.synapsePassword = Hashing.md5().hashBytes(this.getPropertyString("password", "must16keyslength").getBytes(StandardCharsets.UTF_8)).toString();
        this.compressionLevel = Math.max(Math.min(this.getPropertyInt("compression-level", 6), 9), 0);
        this.playersPerThread = this.getPropertyInt("players-per-thread", 30);
        this.plusOnePlayerCount = this.getPropertyBoolean("plus-one-max-count", true);
        this.motd = this.getPropertyString("motd", "Nemisys Proxy");
        this.ip = this.getPropertyString("server-ip", "0.0.0.0");
        this.port = this.getPropertyInt("server-port", 19132);
        this.maxPlayers = this.getPropertyInt("max-players", 50);
        this.queryVersion = this.getPropertyString("query-version", "1.19.0");
        callDataPkSendEv = this.getPropertyBoolean("call-data-pk-send-ev", false);
        callDataPkReceiveEv = this.getPropertyBoolean("call-data-pk-receive-ev", false);
        dataLimit = this.getPropertyInt("data-limit", 2621440);
        handleChat = this.getPropertyBoolean("handle-chat", true);
        packetLimit = this.getPropertyInt("packet-limit", 1000);

        ServerScheduler.WORKERS = (int) poolSize;
        this.scheduler = new ServerScheduler();

        Nemisys.DEBUG = this.getPropertyInt("debug", 1);
        this.logger.setLogDebug(Nemisys.DEBUG > 1);

        this.network = new Network(this);
        this.network.setName(this.getMotd());

        this.consoleSender = new ConsoleCommandSender();
        this.commandMap = new SimpleCommandMap(this);

        this.pluginManager = new PluginManager(this, this.commandMap);
        this.pluginManager.registerInterface(JavaPluginLoader.class);
        this.queryRegenerateEvent = new QueryRegenerateEvent(this, 5);
        this.network.registerInterface(new RakNetInterface(this));
        this.synapseInterface = new SynapseInterface(this, this.getSynapseIp(), this.getSynapsePort());

        this.logger.info(this.getLanguage().translateString("\u00A7b[\u00A7cNemisys \u00A7aPetteriM1 Edition\u00A7b] Proxy started on {%0}:{%1}", new String[]{this.getIp().isEmpty() ? "*" : this.getIp(), String.valueOf(this.getPort())}));

        this.pluginManager.loadPlugins(this.pluginPath);
        this.enablePlugins();

        if (this.getPropertyBoolean("enable-synapse-client")) {
            try {
                this.synapse = new Synapse(this);
            } catch (Exception e) {
                this.logger.error("Failed to enable Synapse client");
                this.logger.logException(e);
            }
        }

        this.properties.save(true);

        if (this.getPropertyBoolean("thread-watchdog", true)) {
            new Watchdog(this, 50000).start();
        }

        enableQuery = this.getPropertyBoolean("enable-query", true);

        this.start();
    }

    public static Server getInstance() {
        return instance;
    }

    public static void broadcastPacket(Collection<Player> players, DataPacket packet) {
        broadcastPacket(players.toArray(new Player[0]), packet);
    }

    public static void broadcastPacket(Player[] players, DataPacket packet) {
        packet.encode();
        packet.isEncoded = true;

        for (Player player : players) {
            player.sendDataPacket(packet);
        }
    }

    public void addClient(Client client) {
        this.clients.put(client.getHash(), client);
        if (client.isLobbyServer()) {
            this.mainServers.put(client.getHash(), client);
        }
    }

    public Client getClient(String hash) {
        return this.clients.get(hash);
    }

    public Client getClientByDesc(String desc) {
        String hash = clientData.getHashByDescription(desc);
        if (hash == null)
            return null;
        return getClient(hash);
    }

    public Map<String, Client> getMainClients() {
        return this.mainServers;
    }

    public Map<String, Client> getLobbyClients() {
        return this.mainServers;
    }

    public void removeClient(Client client) {
        if (this.clients.containsKey(client.getHash())) {
            this.mainServers.remove(client.getHash());
            this.clients.remove(client.getHash());
        }
    }

    public Map<String, Client> getClients() {
        return this.clients;
    }

    public ClientData getClientData() {
        return clientData;
    }

    public String getClientDataJson() {
        return clientDataJson;
    }

    public void updateClientData() {
        if (this.clients.size() > 0) {
            this.clientData = new ClientData();
            for (Client client : this.clients.values()) {
                ClientData.Entry entry = new ClientData.Entry(client.getIp(), client.getPort(), client.getPlayers().size(),
                        client.getMaxPlayers(), client.getDescription(), client.getTps(), client.getLoad(), client.getUpTime());
                this.clientData.clientList.put(client.getHash(), entry);
            }
            this.clientDataJson = GSON.toJson(this.clientData);
        }
    }

    public boolean comparePassword(String pass) {
        return synapsePassword.equals(pass);
    }

    public void enablePlugins() {
        this.pluginManager.getPlugins().values().forEach((p) -> {
            if (!p.isEnabled()) {
                enablePlugin(p);
            }
        });

        DefaultPermissions.registerCorePermissions();
    }

    public void enablePlugin(Plugin plugin) {
        this.pluginManager.enablePlugin(plugin);
    }

    public void disablePlugins() {
        this.pluginManager.disablePlugins();
    }

    public boolean dispatchCommand(CommandSender sender, String commandLine) throws ServerException {
        return dispatchCommand(sender, commandLine, true);
    }

    public boolean dispatchCommand(CommandSender sender, String commandLine, boolean notify) throws ServerException {
        if (sender == null) {
            throw new ServerException("CommandSender is not valid");
        }

        if (this.commandMap.dispatch(sender, commandLine)) {
            return true;
        }

        if (notify)
            sender.sendMessage(new TranslationContainer(TextFormat.RED + "Unknown command. Try /help for a list of commands"));

        return false;
    }

    public ConsoleCommandSender getConsoleSender() {
        return consoleSender;
    }

    public void shutdown() {
        if (!isRunning.compareAndSet(true, false)) {
            throw new IllegalStateException("Server has already shutdown");
        }
    }

    public void forceShutdown() {
        this.forceShutdown("§cProxy server closed");
    }
    public void forceShutdown(String reason) {
        if (this.hasStopped) {
            return;
        }

        try {
            isRunning.compareAndSet(true, false);

            this.hasStopped = true;

            this.getLogger().debug("Disconnecting all clients...");
            for (Client client : new ArrayList<>(this.clients.values())) {
                for (Player player : new ArrayList<>(client.getPlayers().values())) {
                    player.close(reason);
                }
                client.close(reason);
            }

            this.getLogger().debug("Disabling all plugins...");
            this.pluginManager.disablePlugins();

            this.getLogger().debug("Removing event handlers...");
            HandlerList.unregisterAll();

            this.getLogger().debug("Stopping all tasks...");
            this.scheduler.cancelAllTasks();
            this.scheduler.mainThreadHeartbeat(Integer.MAX_VALUE);

            this.getLogger().debug("Closing console...");
            this.console.interrupt();

            this.getLogger().debug("Stopping network interfaces...");
            for (SourceInterface interfaz : new ArrayList<>(this.network.getInterfaces())) {
                interfaz.shutdown();
                this.network.unregisterInterface(interfaz);
            }

            this.getLogger().debug("Stopping Synapse...");
            if (this.synapse != null) {
                for (SynapseEntry entry : this.synapse.getSynapseEntries().values()) {
                    entry.getSynapseInterface().shutdown();
                }
            }
            this.synapseInterface.getInterface().shutdown();
        } catch (Exception e) {
            this.logger.logException(e);
            this.logger.emergency("Exception happened while shutting down, exit the process");
            System.exit(1);
        }
    }

    public void start() {
        if (this.getPropertyBoolean("send-start-message", false)) {
            this.getLogger().info("Done (" + (double) (System.currentTimeMillis() - Nemisys.START_TIME) / 1000 + "s)! For help, type \"help\"");
        }

        if (enableQuery) {
            this.queryHandler = new QueryHandler();
        }

        this.tickCounter = 0;

        this.tickProcessor();
        this.forceShutdown();
    }

    private static final byte[] PREFIX = {(byte) 0xfe, (byte) 0xfd};

    public void handlePacket(InetSocketAddress address, ByteBuf payload) {
        try {
            if (!payload.isReadable(3)) {
                return;
            }
            byte[] prefix = new byte[2];
            payload.readBytes(prefix);

            if (!Arrays.equals(prefix, PREFIX)) {
                return;
            }
            if (this.queryHandler != null) {
                this.queryHandler.handle(address, payload);
            }
        } catch (Exception e) {
            this.logger.logException(e);
            this.network.blockAddress(address.getAddress(), 5);
        }
    }

    public void tickProcessor() {
        this.nextTick = System.currentTimeMillis();
        while (this.isRunning.get()) {
            try {
                this.tick();
            } catch (RuntimeException e) {
                this.getLogger().logException(e);
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Server.getInstance().getLogger().logException(e);
            }
        }
    }

    public void addPlayer(InetSocketAddress socketAddress, Player player) {
        this.players.put(socketAddress, player);
        adjustPoolSize();
    }

    public void addOnlinePlayer(UUID uuid, Player player) {
        this.playersUUIDs.put(uuid, player);
    }

    private void tick() {
        long tickTime = System.currentTimeMillis();
        if ((tickTime - this.nextTick) < -5) {
            return;
        }

        long tickTimeNano = System.nanoTime();
        ++this.tickCounter;

        this.network.processInterfaces();
        this.synapseInterface.process();
        this.scheduler.mainThreadHeartbeat(this.tickCounter);

        for (Player player : new ArrayList<>(this.players.values())) {
            playerTicker.execute(() -> {
                if (player.canTick()) {
                    player.onUpdate(this.tickCounter);
                }
            });
        }

        for (Client client : new ArrayList<>(this.clients.values())) {
            client.onUpdate(this.tickCounter);
        }

        if ((this.tickCounter & 0b1111) == 0) {
            if ((this.tickCounter & 0b111111) == 0) {
                this.titleTick();
            }

            this.maxTick = 100;
            this.maxUse = 0;

            if ((this.tickCounter & 0b111111111) == 0) {
                try {
                    this.getPluginManager().callEvent(this.queryRegenerateEvent = new QueryRegenerateEvent(this, 5));
                    if (this.queryHandler != null) {
                        this.queryHandler.regenerateInfo();
                    }
                } catch (Exception e) {
                    this.logger.logException(e);
                }
            }

            this.getNetwork().updateName();
        }

        long nowNano = System.nanoTime();

        float tick = (float) Math.min(100, 1000000000 / Math.max(1000000, ((double) nowNano - tickTimeNano)));
        float use = (float) Math.min(1, ((double) (nowNano - tickTimeNano)) / 50000000);

        if (this.maxTick > tick) {
            this.maxTick = tick;
        }

        if (this.maxUse < use) {
            this.maxUse = use;
        }

        System.arraycopy(this.tickAverage, 1, this.tickAverage, 0, this.tickAverage.length - 1);
        this.tickAverage[this.tickAverage.length - 1] = tick;

        System.arraycopy(this.useAverage, 1, this.useAverage, 0, this.useAverage.length - 1);
        this.useAverage[this.useAverage.length - 1] = use;

        if ((this.nextTick - tickTime) < -1000) {
            this.nextTick = tickTime;
        } else {
            this.nextTick += 10;
        }
    }

    private void titleTick() {
        if (Nemisys.ANSI) {
            System.out.print((char) 0x1b + "]0;Nemisys Proxy" +
                    " | Players: " + this.players.size() +
                    " | Servers: " + this.clients.size() +
                    " | Memory: " + Math.round(NemisysMath.round((double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024, 2)) + " MB" +
                    " | TPS: " + this.getTicksPerSecond() +
                    " | Load: " + this.getTickUsage() + '%' + (char) 0x07);
        }

        //this.network.resetStatistics();
    }

    public QueryRegenerateEvent getQueryInformation() {
        return this.queryRegenerateEvent;
    }

    public String getName() {
        return "Nemisys PetteriM1 Edition";
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public String getVersion() {
        return queryVersion;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getDataPath() {
        return dataPath;
    }

    public String getPluginPath() {
        return pluginPath;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getPort() {
        return port;
    }

    public String getIp() {
        return ip;
    }

    public int getSynapsePort() {
        return this.getPropertyInt("synapse-port", 10305);
    }

    public String getSynapseIp() {
        return this.getPropertyString("synapse-ip", "0.0.0.0");
    }

    public String getMotd() {
        return motd;
    }

    public MainLogger getLogger() {
        return this.logger;
    }

    public PluginManager getPluginManager() {
        return this.pluginManager;
    }

    public ServerScheduler getScheduler() {
        return scheduler;
    }

    public int getTick() {
        return tickCounter;
    }

    public float getTicksPerSecond() {
        return ((float) Math.round(this.maxTick * 100)) / 100;
    }

    public float getTicksPerSecondAverage() {
        float sum = 0;
        int count = this.tickAverage.length;
        for (float aTickAverage : this.tickAverage) {
            sum += aTickAverage;
        }
        return (float) NemisysMath.round(sum / count, 2);
    }

    public float getTickUsage() {
        return (float) NemisysMath.round(this.maxUse * 100, 2);
    }

    public float getTickUsageAverage() {
        float sum = 0;
        for (float aUseAverage : this.useAverage) {
            sum += aUseAverage;
        }
        return ((float) Math.round(sum / this.useAverage.length * 100)) / 100;
    }

    public SimpleCommandMap getCommandMap() {
        return commandMap;
    }

    public Map<InetSocketAddress, Player> getOnlinePlayers() {
        return this.players;
    }

    public Player getPlayer(UUID uuid) {
        return playersUUIDs.get(uuid);
    }

    public Player getPlayer(String name) {
        Player found = null;
        name = name.toLowerCase();
        int delta = Integer.MAX_VALUE;
        for (Player player : this.getOnlinePlayers().values()) {
            if (player.getName().toLowerCase().startsWith(name)) {
                int curDelta = player.getName().length() - name.length();
                if (curDelta < delta) {
                    found = player;
                    delta = curDelta;
                }
                if (curDelta == 0) {
                    break;
                }
            }
        }

        return found;
    }

    public Player getPlayerExact(String name) {
        name = name.toLowerCase();
        for (Player player : this.getOnlinePlayers().values()) {
            if (player.getName().toLowerCase().equals(name)) {
                return player;
            }
        }

        return null;
    }

    public Player[] matchPlayer(String partialName) {
        partialName = partialName.toLowerCase();
        List<Player> matchedPlayer = new ArrayList<>();
        for (Player player : this.getOnlinePlayers().values()) {
            if (player.getName().toLowerCase().equals(partialName)) {
                return new Player[]{player};
            } else if (player.getName().toLowerCase().contains(partialName)) {
                matchedPlayer.add(player);
            }
        }

        return matchedPlayer.toArray(new Player[0]);
    }

    public void removePlayer(Player player) {
        if (player.getUuid() != null) {
            this.playersUUIDs.remove(player.getUuid());
        }

        for (InetSocketAddress socketAddress : new ArrayList<>(this.players.keySet())) {
            Player p = this.players.get(socketAddress);
            if (player == p) {
                this.players.remove(socketAddress);
                break;
            }
        }

        adjustPoolSize();
    }

    public BaseLang getLanguage() {
        return baseLang;
    }

    public Network getNetwork() {
        return network;
    }

    public Object getConfig(String variable) {
        return this.getConfig(variable, null);
    }

    public Object getConfig(String variable, Object defaultValue) {
        Object value = this.properties.get(variable);
        return value == null ? defaultValue : value;
    }

    public Object getProperty(String variable) {
        return this.getProperty(variable, null);
    }

    public Object getProperty(String variable, Object defaultValue) {
        return this.properties.exists(variable) ? this.properties.get(variable) : defaultValue;
    }

    public void setPropertyString(String variable, String value) {
        this.properties.set(variable, value);
        this.properties.save();
    }

    public String getPropertyString(String variable) {
        return this.getPropertyString(variable, null);
    }

    public String getPropertyString(String variable, String defaultValue) {
        return this.properties.exists(variable) ? (String) this.properties.get(variable) : defaultValue;
    }

    public int getPropertyInt(String variable) {
        return this.getPropertyInt(variable, null);
    }

    public int getPropertyInt(String variable, Integer defaultValue) {
        return this.properties.exists(variable) ? (!this.properties.get(variable).equals("") ? Integer.parseInt(String.valueOf(this.properties.get(variable))) : defaultValue) : defaultValue;
    }

    public void setPropertyInt(String variable, int value) {
        this.properties.set(variable, value);
        this.properties.save();
    }

    public boolean getPropertyBoolean(String variable) {
        return this.getPropertyBoolean(variable, null);
    }

    public boolean getPropertyBoolean(String variable, Object defaultValue) {
        Object value = this.properties.exists(variable) ? this.properties.get(variable) : defaultValue;
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        switch (String.valueOf(value)) {
            case "on":
            case "true":
            case "1":
            case "yes":
                return true;
        }
        return false;
    }

    public void setPropertyBoolean(String variable, boolean value) {
        this.properties.set(variable, value ? "1" : "0");
        this.properties.save();
    }

    public PluginIdentifiableCommand getPluginCommand(String name) {
        Command command = this.commandMap.getCommand(name);
        if (command instanceof PluginIdentifiableCommand) {
            return (PluginIdentifiableCommand) command;
        } else {
            return null;
        }
    }

    public SynapseInterface getSynapseInterface() {
        return synapseInterface;
    }

    public Synapse getSynapse() {
        return synapse;
    }

    public void batchPacket(Player player, DataPacket packet) {
        if (player == null || packet == null) {
            return;
        }

        BinaryStream batched = new BinaryStream();
        if (packet instanceof BatchPacket) {
            throw new RuntimeException("Cannot batch BatchPacket");
        }
        if (!packet.isEncoded) {
            packet.encode();
            packet.isEncoded = true;
        }
        byte[] buf = packet.getBuffer();
        batched.putUnsignedVarInt(buf.length);
        batched.put(buf);

        batchCommon(player, batched);
    }

    void batchPackets(Player player, DataPacket[] packets) {
        if (player == null || packets == null || packets.length == 0) {
            return;
        }

        BinaryStream batched = new BinaryStream();
        for (DataPacket packet : packets) {
            if (packet instanceof BatchPacket) {
                throw new RuntimeException("Cannot batch BatchPacket");
            }
            if (!packet.isEncoded) {
                packet.encode();
                packet.isEncoded = true;
            }
            byte[] buf = packet.getBuffer();
            batched.putUnsignedVarInt(buf.length);
            batched.put(buf);
        }

        batchCommon(player, batched);
    }

    void batchPackets(Player player, List<DataPacket> packets) {
        if (player == null || packets == null || packets.isEmpty()) {
            return;
        }

        BinaryStream batched = new BinaryStream();
        for (DataPacket packet : packets) {
            if (packet instanceof BatchPacket) {
                throw new RuntimeException("Cannot batch BatchPacket");
            }
            if (!packet.isEncoded) {
                packet.encode();
                packet.isEncoded = true;
            }
            byte[] buf = packet.getBuffer();
            batched.putUnsignedVarInt(buf.length);
            batched.put(buf);
        }

        batchCommon(player, batched);
    }

    private void batchCommon(Player player, BinaryStream batched) {
        if (!player.closed) {
            try {
                byte[] bytes = Binary.appendBytes(batched.getBuffer());
                BatchPacket pk = new BatchPacket();
                if (player.raknetProtocol >= 10) {
                    pk.payload = Zlib.deflateRaw(bytes, compressionLevel);
                } else {
                    pk.payload = Zlib.deflate(bytes, compressionLevel);
                }
                player.sendDataPacket(pk, true, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Client getFallbackClient() {
        List<Client> list = new ArrayList<>();
        for (Client c : clients.values()) {
            if (c.isLobbyServer()) {
                list.add(c);
            }
        }

        if (list.isEmpty()) {
            return null;
        }

        if (list.size() == 1) {
            return list.get(0);
        }

        return list.get(Utils.random.nextInt(list.size() - 1));
    }

    private void adjustPoolSize() {
        int threads = Math.min(Math.max(1, players.size() / this.playersPerThread), Runtime.getRuntime().availableProcessors());
        if (playerTicker.getCorePoolSize() != threads) {
            playerTicker.setCorePoolSize(threads);
        }
    }

    public Thread getPrimaryThread() {
        return currentThread;
    }

    public long getNextTick() {
        return nextTick;
    }

    private static class ServerProperties extends ConfigSection {
        {
            put("motd", "Nemisys Proxy");
            put("server-ip", "0.0.0.0");
            put("server-port", 19132);
            put("synapse-ip", "0.0.0.0");
            put("synapse-port", 10305);
            put("password", "must16keyslength");
            put("async-workers", "auto");
            put("max-players", 50);
            put("plus-one-max-count", true);
            put("players-per-thread", 30);
            put("enable-query", true);
            put("debug", 1);
            put("enable-synapse-client", false);
            put("ansi", true);
            put("send-start-message", false);
            put("compression-level", 6);
            put("query-version", "1.19.0");
            put("data-limit", 2621440);
            put("thread-watchdog", true);
            put("call-data-pk-send-ev", false);
            put("call-data-pk-receive-ev", false);
            put("handle-chat", true);
            put("min-mtu", 576);
            put("max-mtu", 1492);
            put("packet-limit", 1000);
        }
    }
}
