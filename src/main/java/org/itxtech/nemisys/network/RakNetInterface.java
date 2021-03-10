package org.itxtech.nemisys.network;

import com.google.common.base.Strings;
import com.nukkitx.network.raknet.*;
import com.nukkitx.network.util.DisconnectReason;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import lombok.RequiredArgsConstructor;
import org.itxtech.nemisys.Player;
import org.itxtech.nemisys.Server;
import org.itxtech.nemisys.event.player.PlayerCreationEvent;
import org.itxtech.nemisys.event.server.QueryRegenerateEvent;
import org.itxtech.nemisys.network.protocol.mcpe.BatchPacket;
import org.itxtech.nemisys.network.protocol.mcpe.DataPacket;
import org.itxtech.nemisys.network.protocol.mcpe.ProtocolInfo;
import org.itxtech.nemisys.utils.Utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class RakNetInterface implements RakNetServerListener, AdvancedSourceInterface {

    private final Server server;

    private Network network;

    private final RakNetServer raknet;

    private final Set<NukkitSessionListener> sessionListeners = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private byte[] advertisement;

    public RakNetInterface(Server server) {
        this.server = server;

        InetSocketAddress bindAddress = new InetSocketAddress(Strings.isNullOrEmpty(this.server.getIp()) ? "0.0.0.0" : this.server.getIp(), this.server.getPort());

        this.raknet = new RakNetServer(bindAddress, Runtime.getRuntime().availableProcessors());
        this.raknet.bind().join();
        this.raknet.setListener(this);
    }

    @Override
    public void setNetwork(Network network) {
        this.network = network;
    }

    @Override
    public boolean process() {
        Iterator<NukkitSessionListener> iterator = this.sessionListeners.iterator();
        while (iterator.hasNext()) {
            NukkitSessionListener listener = iterator.next();
            Player player = listener.player;
            if (listener.disconnectReason != null) {
                player.close(listener.disconnectReason, false);
                iterator.remove();
                continue;
            }
            DataPacket packet;
            while ((packet = listener.packets.poll()) != null) {
                listener.player.handleDataPacket(packet);
            }
        }
        return true;
    }

    @Override
    public int getNetworkLatency(Player player) {
        RakNetServerSession session = this.raknet.getSession(player.getSocketAddress());
        return session == null ? -1 : (int) session.getPing();
    }

    @Override
    public void close(Player player) {
        this.close(player, "unknown reason");
    }

    @Override
    public void close(Player player, String reason) {
        RakNetServerSession session = this.raknet.getSession(player.getSocketAddress());
        if (session != null) {
            session.close();
        }
    }

    @Override
    public void shutdown() {
        this.raknet.close();
    }

    @Override
    public void emergencyShutdown() {
        this.raknet.close();
    }

    @Override
    public void blockAddress(InetAddress address) {
        this.raknet.block(address);
    }

    @Override
    public void blockAddress(InetAddress address, int timeout) {
        this.raknet.block(address, timeout, TimeUnit.SECONDS);
    }

    @Override
    public void sendRawPacket(InetSocketAddress socketAddress, ByteBuf payload) {
        this.raknet.send(socketAddress, payload);
    }

    @Override
    public void setName(String name) {
        QueryRegenerateEvent info = this.server.getQueryInformation();
        String[] names = name.split("!@#");
        StringJoiner joiner = new StringJoiner(";")
                .add("MCPE")
                .add(Utils.rtrim(names[0].replace(";", "\\;"), '\\'))
                .add(Integer.toString(407))
                .add(server.getVersion())
                .add(Integer.toString(info.getPlayerCount()))
                .add(Integer.toString(info.getMaxPlayerCount()))
                .add(Long.toString(this.raknet.getGuid()))
                .add(names.length > 1 ? Utils.rtrim(names[1].replace(";", "\\;"), '\\') : "")
                .add("Survival")
                .add("1");

        this.advertisement = joiner.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Integer putPacket(Player player, DataPacket packet) {
        return this.putPacket(player, packet, false);
    }

    @Override
    public Integer putPacket(Player player, DataPacket packet, boolean needACK) {
        return this.putPacket(player, packet, needACK, false);
    }

    @Override
    public Integer putPacket(Player player, DataPacket packet, boolean needACK, boolean immediate) {
        RakNetServerSession session = this.raknet.getSession(player.getSocketAddress());
        if (session == null) {
            return null;
        }

        byte[] buffer;
        if (packet.pid() == ProtocolInfo.BATCH_PACKET) {
            buffer = ((BatchPacket) packet).payload;
            if (buffer == null) {
                return null;
            }
        } else {
            this.server.batchPackets(new Player[]{player}, new DataPacket[]{packet});
            return null;
        }

        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.ioBuffer(1 + buffer.length);
        byteBuf.writeByte(0xfe);
        byteBuf.writeBytes(buffer);
        //byteBuf.readerIndex(0);
        buffer = null;

        session.send(byteBuf, immediate ? RakNetPriority.IMMEDIATE : RakNetPriority.MEDIUM, packet.reliability,
                packet.getChannel());

        return null;
    }

    @Override
    public boolean onConnectionRequest(InetSocketAddress inetSocketAddress) {
        return true;
    }

    @Override
    public byte[] onQuery(InetSocketAddress inetSocketAddress) {
        return this.advertisement;
    }

    @Override
    public void onSessionCreation(RakNetServerSession session) {
        PlayerCreationEvent ev = new PlayerCreationEvent(this, Player.class, Player.class, 0L, session.getAddress());
        this.server.getPluginManager().callEvent(ev);
        Class<? extends Player> clazz = ev.getPlayerClass();

        try {
            Constructor constructor = clazz.getConstructor(SourceInterface.class, Long.class, InetSocketAddress.class);
            Player player = (Player) constructor.newInstance(this, ev.getClientId(), ev.getSocketAddress());
            player.raknetProtocol = session.protocol;
            this.server.addPlayer(session.getAddress(), player);
            NukkitSessionListener listener = new NukkitSessionListener(player);
            this.sessionListeners.add(listener);
            session.setListener(listener);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            Server.getInstance().getLogger().logException(e);
        }
    }

    @Override
    public void onUnhandledDatagram(ChannelHandlerContext ctx, DatagramPacket datagramPacket) {
        this.server.handlePacket(datagramPacket.sender(), datagramPacket.content());
    }

    @RequiredArgsConstructor
    private class NukkitSessionListener implements RakNetSessionListener {
        private final Player player;
        private final Queue<DataPacket> packets = new ConcurrentLinkedQueue<>();
        private String disconnectReason = null;

        @Override
        public void onSessionChangeState(RakNetState rakNetState) {

        }

        @Override
        public void onDisconnect(DisconnectReason disconnectReason) {
            if (disconnectReason == DisconnectReason.TIMED_OUT) {
                this.disconnectReason = "Timed out";
            } else {
                this.disconnectReason = "Disconnected from Server";
            }
        }

        @Override
        public void onEncapsulated(EncapsulatedPacket packet) {
            ByteBuf buffer = packet.getBuffer();
            short packetId = buffer.readUnsignedByte();
            if (packetId == 0xfe) {
                DataPacket batchPacket = RakNetInterface.this.network.getPacket(ProtocolInfo.BATCH_PACKET);
                if (batchPacket == null) {
                    return;
                }

                byte[] packetBuffer = new byte[buffer.readableBytes()];
                buffer.readBytes(packetBuffer);
                batchPacket.setBuffer(packetBuffer);
                batchPacket.decode();

                packets.offer(batchPacket);
            }
        }

        @Override
        public void onDirect(ByteBuf byteBuf) {
            // We don't allow any direct packets so ignore.
        }
    }
}
