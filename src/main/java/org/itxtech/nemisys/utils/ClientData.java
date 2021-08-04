package org.itxtech.nemisys.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by boybook on 16/6/25.
 */
public class ClientData {

    public Map<String, Entry> clientList = new ConcurrentHashMap<>();

    public String getHashByDescription(String description) {
        final String[] re = new String[1];
        this.clientList.forEach((hash, entry) -> {
            if (entry.getDescription().equals(description)) {
                re[0] = hash;
            }
        });
        return re[0];
    }

    public static class Entry {
        private final String ip;
        private final int port;
        private final int playerCount;
        private final int maxPlayers;
        private final String description;
        private final float tps;
        private final float load;
        private final long upTime;

        public Entry(String ip, int port, int playerCount, int maxPlayers, String description, float tps, float load, long upTime) {
            this.ip = ip;
            this.port = port;
            this.playerCount = playerCount;
            this.maxPlayers = maxPlayers;
            this.description = description;
            this.tps = tps;
            this.load = load;
            this.upTime = upTime;
        }

        public String getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }

        public int getMaxPlayers() {
            return maxPlayers;
        }

        public int getPlayerCount() {
            return playerCount;
        }

        public String getDescription() {
            return description;
        }

        public float getTicksPerSecond() {
            return this.tps;
        }

        public float getTickUsage() {
            return this.load;
        }

        public long getUpTime() {
            return this.upTime;
        }
    }
}
