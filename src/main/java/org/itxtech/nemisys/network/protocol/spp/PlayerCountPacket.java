package org.itxtech.nemisys.network.protocol.spp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerCountPacket extends SynapseDataPacket {

    public Map<String, Integer> data;

    @Override
    public byte pid() {
        return SynapseInfo.PLAYER_COUNT_PACKET;
    }

    @Override
    public void encode() {
        this.reset();
        this.putInt(data.size());
        this.data.forEach((name, count) -> {
            this.putString(name);
            this.putInt(count);
        });
    }

    @Override
    public void decode() {
        this.data = new ConcurrentHashMap<>();
        int size = this.getInt();
        for (int i = 0; i < size; i++) {
            this.data.put(this.getString(), this.getInt());
        }
    }
}
