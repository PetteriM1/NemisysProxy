package org.itxtech.nemisys.network.protocol.mcpe;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.itxtech.nemisys.Server;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LoginPacket extends DataPacket {

    public String username;
    public int protocol;
    public UUID clientUUID;
    public long clientId;
    public byte[] cacheBuffer;

    private static final Gson GSON = new Gson();

    @Override
    public byte pid() {
        return ProtocolInfo.LOGIN_PACKET;
    }

    @Override
    public void decode() {
        this.cacheBuffer = this.getBuffer();
        this.protocol = this.getInt();
        this.setBuffer(this.getByteArray(), 0);
        decodeChainData();
    }

    @Override
    public void encode() {
    }

    public int getProtocol() {
        return protocol;
    }

    private void decodeChainData() {
        int size = this.getLInt();
        if (size > Server.dataLimit) {
            throw new IllegalArgumentException("The chain data is too big: " + size);
        }

        String data = new String(this.get(size), StandardCharsets.UTF_8);
        Map<String, List<String>> map = GSON.fromJson(data, new MapTypeToken().getType());

        if (map.isEmpty() || !map.containsKey("chain") || map.get("chain").isEmpty()) {
            return;
        }
        List<String> chains = map.get("chain");
        for (String c : chains) {
            JsonObject chainMap = decodeToken(c);
            if (chainMap == null) continue;
            if (chainMap.has("extraData")) {
                JsonObject extra = chainMap.get("extraData").getAsJsonObject();
                if (extra.has("displayName")) this.username = extra.get("displayName").getAsString();
                if (extra.has("identity")) this.clientUUID = UUID.fromString(extra.get("identity").getAsString());
            }
        }
    }

    private JsonObject decodeToken(String token) {
        String[] base = token.split("\\.");
        if (base.length < 2) return null;
        return GSON.fromJson(new String(Base64.getDecoder().decode(base[1].replaceAll("-", "+").replaceAll("_", "/")), StandardCharsets.UTF_8), JsonObject.class);
    }

    private static class MapTypeToken extends TypeToken<Map<String, List<String>>> {
    }
}
