package org.itxtech.nemisys.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.itxtech.nemisys.Server;
import org.itxtech.nemisys.network.protocol.mcpe.LoginPacket;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * ClientChainData is a container of chain data sent from clients.
 *
 * Device information such as client UUID, xuid and serverAddress, can be
 * read from instances of this object.
 *
 * To get chain data, you can use player.getLoginChainData() or read(loginPacket)
 *
 * ===============
 * @author boybook
 * Nukkit Project
 * ===============
 */
public final class ClientChainData {

    public final static int UI_PROFILE_CLASSIC = 0;
    public final static int UI_PROFILE_POCKET = 1;
    private String username;
    private UUID clientUUID;
    private String xuid;
    private String identityPublicKey;
    private long clientId;
    private String serverAddress;
    private String deviceModel;
    private int deviceOS;
    private String gameVersion;
    private int guiScale;
    private String languageCode;
    private int currentInputMode;
    private int defaultInputMode;
    private String ADRole;
    private String tenantId;
    private int UIProfile;
    private final BinaryStream bs = new BinaryStream();
    private static final Gson GSON = new Gson();

    private ClientChainData(byte[] buffer) {
        bs.setBuffer(buffer, 0);
        decodeChainData();
        decodeSkinData();
    }

    public static ClientChainData of(byte[] buffer) {
        return new ClientChainData(buffer);
    }

    public static ClientChainData read(LoginPacket pk) {
        return of(pk.getBuffer());
    }

    public String getUsername() {
        return username;
    }

    public UUID getClientUUID() {
        return clientUUID;
    }

    public String getIdentityPublicKey() {
        return identityPublicKey;
    }

    public long getClientId() {
        return clientId;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public int getDeviceOS() {
        return deviceOS;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public int getGuiScale() {
        return guiScale;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String getXUID() {
        return xuid;
    }

    public int getCurrentInputMode() {
        return currentInputMode;
    }

    public int getDefaultInputMode() {
        return defaultInputMode;
    }

    public String getADRole() {
        return ADRole;
    }

    public String getTenantId() {
        return tenantId;
    }

    public int getUIProfile() {
        return UIProfile;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ClientChainData && Objects.equals(bs, ((ClientChainData) obj).bs);
    }

    @Override
    public int hashCode() {
        return bs.hashCode();
    }

    private void decodeChainData() {
        int size = bs.getLInt();
        if (size > Server.getInstance().dataLimit) {
            throw new RuntimeException("The chain data is too big: " + size);
        }

        Map<String, List<String>> map = GSON.fromJson(new String(bs.get(size), StandardCharsets.UTF_8),
                new MapTypeToken().getType());
        if (map.isEmpty() || !map.containsKey("chain") || map.get("chain").isEmpty()) return;
        for (String c : map.get("chain")) {
            JsonObject chainMap = decodeToken(c);
            if (chainMap == null) continue;
            if (chainMap.has("extraData")) {
                JsonObject extra = chainMap.get("extraData").getAsJsonObject();
                if (extra.has("displayName")) this.username = extra.get("displayName").getAsString();
                if (extra.has("identity")) this.clientUUID = UUID.fromString(extra.get("identity").getAsString());
                if (extra.has("XUID")) this.xuid = extra.get("XUID").getAsString();
            }
            if (chainMap.has("identityPublicKey"))
                this.identityPublicKey = chainMap.get("identityPublicKey").getAsString();
        }
    }

    private void decodeSkinData() {
        int size = bs.getLInt();
        if (size > Server.getInstance().dataLimit) {
            throw new SkinException("The skin data is too big: " + size);
        }

        JsonObject skinToken = decodeToken(new String(bs.get(size), StandardCharsets.UTF_8));
        if (skinToken == null) return;
        if (skinToken.has("ClientRandomId")) this.clientId = skinToken.get("ClientRandomId").getAsLong();
        if (skinToken.has("ServerAddress")) this.serverAddress = skinToken.get("ServerAddress").getAsString();
        if (skinToken.has("DeviceModel")) this.deviceModel = skinToken.get("DeviceModel").getAsString();
        if (skinToken.has("DeviceOS")) this.deviceOS = skinToken.get("DeviceOS").getAsInt();
        if (skinToken.has("GameVersion")) this.gameVersion = skinToken.get("GameVersion").getAsString();
        if (skinToken.has("GuiScale")) this.guiScale = skinToken.get("GuiScale").getAsInt();
        if (skinToken.has("LanguageCode")) this.languageCode = skinToken.get("LanguageCode").getAsString();
        if (skinToken.has("CurrentInputMode")) this.currentInputMode = skinToken.get("CurrentInputMode").getAsInt();
        if (skinToken.has("DefaultInputMode")) this.defaultInputMode = skinToken.get("DefaultInputMode").getAsInt();
        if (skinToken.has("ADRole")) this.ADRole = skinToken.get("ADRole").getAsString();
        if (skinToken.has("TenantId")) this.tenantId = skinToken.get("TenantId").getAsString();
        if (skinToken.has("UIProfile")) this.UIProfile = skinToken.get("UIProfile").getAsInt();
    }

    private static JsonObject decodeToken(String token) {
        String[] base = token.split("\\.");
        if (base.length < 2) return null;
        return GSON.fromJson(new String(Base64.getDecoder().decode(base[1]), StandardCharsets.UTF_8), JsonObject.class);
    }

    private static class MapTypeToken extends TypeToken<Map<String, List<String>>> {
    }
}
