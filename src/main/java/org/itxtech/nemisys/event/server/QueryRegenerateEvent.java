package org.itxtech.nemisys.event.server;

import org.itxtech.nemisys.Player;
import org.itxtech.nemisys.Server;
import org.itxtech.nemisys.event.HandlerList;
import org.itxtech.nemisys.plugin.Plugin;
import org.itxtech.nemisys.utils.Binary;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class QueryRegenerateEvent extends ServerEvent {

    private static final HandlerList handlers = new HandlerList();
    private int timeout;
    private String serverName;
    private Player[] players;
    private String version;
    private String server_engine;
    private String map;
    private int numPlayers;
    private int maxPlayers;
    private String whitelist;
    private int port;
    private String ip;

    public QueryRegenerateEvent(Server server) {
        this(server, 5);
    }

    public QueryRegenerateEvent(Server server, int timeout) {
        this.timeout = timeout;
        this.serverName = server.getMotd();
        this.players = new ArrayList<>(server.getOnlinePlayers().values()).toArray(new Player[0]);
        this.version = server.getVersion();
        this.server_engine = "Nemisys PetteriM1 Edition";
        this.map = " ";
        this.numPlayers = this.players.length;
        if (server.getPropertyBoolean("plus-one-max-count", true)) {
            this.maxPlayers = this.numPlayers + 1;
        } else {
            this.maxPlayers = server.getMaxPlayers();
        }
        this.whitelist = "off";
        this.ip = server.getIp();
        this.port = server.getPort();
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public boolean canListPlugins() {
        return false;
    }

    public void setListPlugins(boolean listPlugins){}

    public Plugin[] getPlugins() {
        return new Plugin[0];
    }

    public void setPlugins(Plugin[] plugins) {}

    public Player[] getPlayerList() {
        return players;
    }

    public void setPlayerList(Player[] players) {
        this.players = players;
    }

    public int getPlayerCount() {
        return this.numPlayers;
    }

    public void setPlayerCount(int count) {
        this.numPlayers = count;
    }

    public int getMaxPlayerCount() {
        return this.maxPlayers;
    }

    public void setMaxPlayerCount(int count) {
        this.maxPlayers = count;
    }

    public String getWorld() {
        return map;
    }

    public void setWorld(String world) {
        this.map = world;
    }

    public Map<String, String> getExtraData() {
        return new HashMap<String, String>();
    }

    public void setExtraData(Map<String, String> extraData) {}

    public byte[] getLongQuery() {
        ByteBuffer query = ByteBuffer.allocate(65536);
        String plist = this.server_engine;

        query.put("splitnum".getBytes());
        query.put((byte) 0x00);
        query.put((byte) 128);
        query.put((byte) 0x00);

        LinkedHashMap<String, String> KVdata = new LinkedHashMap<>();
        KVdata.put("hostname", this.serverName);
        KVdata.put("gametype", "SMP");
        KVdata.put("game_id", "MINECRAFTPE");
        KVdata.put("version", this.version);
        KVdata.put("server_engine", this.server_engine);
        KVdata.put("plugins", plist);
        KVdata.put("map", this.map);
        KVdata.put("numplayers", String.valueOf(this.numPlayers));
        KVdata.put("maxplayers", String.valueOf(this.maxPlayers));
        KVdata.put("whitelist", this.whitelist);
        KVdata.put("hostip", this.ip);
        KVdata.put("hostport", String.valueOf(this.port));

        for (Map.Entry<String, String> entry : KVdata.entrySet()) {
            query.put(entry.getKey().getBytes(StandardCharsets.UTF_8));
            query.put((byte) 0x00);
            query.put(entry.getValue().getBytes(StandardCharsets.UTF_8));
            query.put((byte) 0x00);
        }

        query.put(new byte[]{0x00, 0x01}).put("player_".getBytes()).put(new byte[]{0x00, 0x00});

        for (Player player : this.players) {
            query.put(player.getName().getBytes(StandardCharsets.UTF_8));
            query.put((byte) 0x00);
        }

        query.put((byte) 0x00);
        return Arrays.copyOf(query.array(), query.position());
    }

    public byte[] getShortQuery() {
        ByteBuffer query = ByteBuffer.allocate(65536);
        query.put(this.serverName.getBytes(StandardCharsets.UTF_8));
        query.put((byte) 0x00);
        query.put("SMP".getBytes(StandardCharsets.UTF_8));
        query.put((byte) 0x00);
        query.put(this.map.getBytes(StandardCharsets.UTF_8));
        query.put((byte) 0x00);
        query.put(String.valueOf(this.numPlayers).getBytes(StandardCharsets.UTF_8));
        query.put((byte) 0x00);
        query.put(String.valueOf(this.maxPlayers).getBytes(StandardCharsets.UTF_8));
        query.put((byte) 0x00);
        query.put(Binary.writeLShort(this.port));
        query.put(this.ip.getBytes(StandardCharsets.UTF_8));
        query.put((byte) 0x00);
        return Arrays.copyOf(query.array(), query.position());
    }
}
