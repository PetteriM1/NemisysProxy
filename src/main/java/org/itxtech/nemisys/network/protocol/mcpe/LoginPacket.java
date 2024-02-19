package org.itxtech.nemisys.network.protocol.mcpe;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.itxtech.nemisys.utils.ClientChainData;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LoginPacket extends DataPacket {

    public String username;
    private int protocol_;
    public UUID clientUUID;

    public byte[] cacheBuffer;

    private static final Gson GSON = new Gson();

    @Override
    public byte pid() {
        return ProtocolInfo.LOGIN_PACKET;
    }

    @Override
    public void decode() {
        this.cacheBuffer = this.getBuffer();
        this.protocol_ = this.getInt();
        if (this.protocol_ == 0) {
            setOffset(getOffset() + 2);
            this.protocol_ = getInt();
        }
        this.setBuffer(this.getByteArray(), 0);
        decodeChainData();
    }

    @Override
    public void encode() {
    }

    public int getProtocol() {
        return protocol_;
    }

    private void decodeChainData() {
        int size = this.getLInt();
        if (size > 3145728) {
            throw new IllegalArgumentException("The chain data is too big: " + size);
        }

        String data = new String(this.get(size), StandardCharsets.UTF_8);

        Map<String, List<String>> map = GSON.fromJson(data, new MapTypeToken().getType());
        if (map.isEmpty() || !map.containsKey("chain") || map.get("chain").isEmpty()) return;

        for (String c : map.get("chain")) {
            JsonObject chainMap = ClientChainData.decodeToken(c);
            if (chainMap == null) continue;
            if (chainMap.has("extraData")) {
                JsonObject extra = chainMap.get("extraData").getAsJsonObject();
                if (extra.has("displayName")) this.username = extra.get("displayName").getAsString();
                if (extra.has("identity")) this.clientUUID = UUID.fromString(extra.get("identity").getAsString());
            }
        }
    }

    private static class MapTypeToken extends TypeToken<Map<String, List<String>>> {
    }
}
