package org.itxtech.nemisys.synapse;

import org.itxtech.nemisys.Server;
import org.itxtech.nemisys.network.RakNetInterface;
import org.itxtech.nemisys.network.SourceInterface;
import org.itxtech.nemisys.network.protocol.mcpe.DataPacket;
import org.itxtech.nemisys.utils.Config;
import org.itxtech.nemisys.utils.MainLogger;
import org.itxtech.nemisys.utils.VarInt;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Synapse
 * ===============
 * @author boybook
 * EaseCation Network Project
 * nemisys
 * ===============
 */
public class Synapse {

    private final Server server;
    private final Config config;

    private final Map<String, SynapseEntry> synapseEntries = new HashMap<>();

    public Synapse(Server server) {
        this.server = server;
        this.server.getLogger().notice("Enabling Synapse Client...");
        this.config = new Config(new File(server.getFilePath() + "/synapse.yml"), Config.YAML);

        String ip = this.getConfig().getString("server-ip", "127.0.0.1");
        int port = this.getConfig().getInt("server-port", 10305);
        boolean isLobbyServer = this.getConfig().getBoolean("isLobbyServer");
        String password = this.getConfig().getString("password");
        String serverDescription = this.getConfig().getString("description");

        for (SourceInterface interfaz : this.getServer().getNetwork().getInterfaces()) {
            if (interfaz instanceof RakNetInterface) {
                if (this.getConfig().getBoolean("disable-rak")) {
                    interfaz.shutdown();
                    break;
                }
            }
        }

        if (!ip.equals("0")) {
            SynapseEntry entry = new SynapseEntry(this, ip, port, isLobbyServer, password, serverDescription);
            this.addSynapseEntry(entry);
        }
    }

    public Config getConfig() {
        return config;
    }

    public Server getServer() {
        return server;
    }

    public MainLogger getLogger() {
        return this.server.getLogger();
    }

    public Map<String, SynapseEntry> getSynapseEntries() {
        return synapseEntries;
    }

    public void addSynapseEntry(SynapseEntry entry) {
        this.synapseEntries.put(entry.getHash(), entry);
    }

    public SynapseEntry getSynapseEntry(String hash) {
        return this.synapseEntries.get(hash);
    }

    public DataPacket getPacket(byte[] buffer) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(buffer);

        int header;
        try {
            header = (int) VarInt.readUnsignedVarInt(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Unable to decode packet header", e);
        }

        // | Client ID | Sender ID | Packet ID |
        // |   2 bits  |   2 bits  |  10 bits  |
        int packetId = header & 0x3ff;

        DataPacket packet = this.getServer().getNetwork().getPacket(packetId == 0xfe ? 0xff : packetId);

        if (packet != null) {
            packet.setBuffer(buffer, buffer.length - inputStream.available());
        }

        return packet;
    }
}
