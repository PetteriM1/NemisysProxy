package org.itxtech.nemisys.event.server;

import org.itxtech.nemisys.Server;
import org.itxtech.nemisys.event.HandlerList;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class QueryRegenerateEvent extends ServerEvent {

    private static final HandlerList handlers = new HandlerList();

    private String serverName;
    private String map;
    private int numPlayers;
    private int maxPlayers;

    public QueryRegenerateEvent(Server server) {
        this(server, 5);
    }

    public QueryRegenerateEvent(Server server, int timeout) {
        this.serverName = server.getMotd();
        this.map = "Proxy";
        this.numPlayers = server.getOnlinePlayers().size();
        if (Server.plusOnePlayerCount) {
            this.maxPlayers = this.numPlayers + 1;
        } else {
            this.maxPlayers = server.getMaxPlayers();
        }
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
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
        return this.map;
    }

    public void setWorld(String world) {
        this.map = world;
    }
}
