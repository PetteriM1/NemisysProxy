package org.itxtech.nemisys.network;

import com.google.common.base.Strings;
import com.nukkitx.network.raknet.*;
import com.nukkitx.network.util.DisconnectReason;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.PlatformDependent;
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
import java.util.concurrent.TimeUnit;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class RakNetInterface implements RakNetServerListener, AdvancedSourceInterface {

    private final Server server;

    private Network network;
    private final RakNetServer raknet;
    private byte[] advertisement;

    private final Map<InetSocketAddress, NukkitRakNetSession> sessions = new HashMap<>();
    private final Queue<NukkitRakNetSession> sessionCreationQueue = PlatformDependent.newMpscQueue();

    public RakNetInterface(Server server) {
        this.server = server;
        this.raknet = new RakNetServer(new InetSocketAddress(Strings.isNullOrEmpty(this.server.getIp()) ? "0.0.0.0" : this.server.getIp(), this.server.getPort()), Runtime.getRuntime().availableProcessors());
        this.raknet.bind().join();
        this.raknet.setListener(this);
    }

    @Override
    public void setNetwork(Network network) {
        this.network = network;
    }

    @Override
    public boolean process() {
        NukkitRakNetSession session;
        while ((session = this.sessionCreationQueue.poll()) != null) {
            InetSocketAddress address = session.raknet.getAddress();
            PlayerCreationEvent ev = new PlayerCreationEvent(this, Player.class, Player.class, 0L, address);
            this.server.getPluginManager().callEvent(ev);
            Class<? extends Player> clazz = ev.getPlayerClass();

            try {
                Constructor<? extends Player> constructor = clazz.getConstructor(SourceInterface.class, Long.class, InetSocketAddress.class);
                Player player = constructor.newInstance(this, ev.getClientId(), ev.getSocketAddress());
                player.raknetProtocol = session.raknet.getProtocolVersion();
                session.player = player;
                this.server.addPlayer(address, player);
                this.sessions.put(address, session);
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                Server.getInstance().getLogger().logException(e);
            }
        }

        Iterator<NukkitRakNetSession> iterator = this.sessions.values().iterator();
        while (iterator.hasNext()) {
            NukkitRakNetSession nukkitSession = iterator.next();
            Player player = nukkitSession.player;
            if (nukkitSession.disconnectReason != null) {
                player.close(nukkitSession.disconnectReason, false);
                iterator.remove();
                continue;
            }
            /*DataPacket packet;
            while ((packet = nukkitSession.packets.poll()) != null) {
                try {
                    nukkitSession.player.handleDataPacket(packet);
                } catch (Exception e) {
                    Server.getInstance().getLogger().error("An error occurred whilst handling " + packet.getClass().getSimpleName() + " for " + nukkitSession.player.getName(), e);
                }
            }*/
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
        this.server.getLogger().info("Blocked " + address + " permanently");
    }

    @Override
    public void blockAddress(InetAddress address, int timeout) {
        this.raknet.block(address, timeout, TimeUnit.SECONDS);
        this.server.getLogger().info("Blocked " + address + " for " + timeout + " seconds");
    }

    public void unblockAddress(InetAddress address) {
        this.raknet.unblock(address);
    }

    @Override
    public void sendRawPacket(InetSocketAddress socketAddress, ByteBuf payload) {
        this.raknet.send(socketAddress, payload);
    }

    @Override
    public void setName(String name) {
        QueryRegenerateEvent info = this.server.getQueryInformation();
        StringJoiner joiner = new StringJoiner(";")
                .add("MCPE")
                .add(Utils.rtrim(name.replace(";", "\\;"), '\\'))
                .add("407")
                .add(server.getVersion())
                .add(Integer.toString(info.getPlayerCount()))
                .add(Integer.toString(info.getMaxPlayerCount()))
                .add(Long.toString(this.raknet.getGuid()))
                .add("Proxy")
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
            this.server.batchPacket(player, packet);
            return null;
        }

        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.directBuffer(1 + buffer.length);
        byteBuf.writeByte(0xfe);
        byteBuf.writeBytes(buffer);

        session.send(byteBuf, immediate ? RakNetPriority.IMMEDIATE : RakNetPriority.MEDIUM, packet.reliability, packet.getChannel());
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
        NukkitRakNetSession nukkitSession = new NukkitRakNetSession(session);
        session.setListener(nukkitSession);
        this.sessionCreationQueue.offer(nukkitSession);
    }

    @Override
    public void onUnhandledDatagram(ChannelHandlerContext ctx, DatagramPacket datagramPacket) {
        this.server.handlePacket(datagramPacket.sender(), datagramPacket.content());
    }

    @RequiredArgsConstructor
    private class NukkitRakNetSession implements RakNetSessionListener {

        private final RakNetServerSession raknet;
        //private final Queue<DataPacket> packets = PlatformDependent.newSpscQueue();
        private String disconnectReason = null;
        private Player player;

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

        public void onEncapsulated(EncapsulatedPacket packet) {
            ByteBuf buffer = packet.getBuffer();
            if (buffer.readUnsignedByte() == 0xfe) {
                DataPacket batchPacket = RakNetInterface.this.network.getPacket(ProtocolInfo.BATCH_PACKET);
                if (batchPacket == null) {
                    return;
                }

                byte[] packetBuffer = new byte[buffer.readableBytes()];
                buffer.readBytes(packetBuffer);
                batchPacket.setBuffer(packetBuffer);
                batchPacket.protocol = player.protocol;
                batchPacket.decode();
                //this.packets.add(batchPacket);
                this.player.addOutgoingPacket(batchPacket); // handleDataPacket on player ticker
            }
        }

        @Override
        public void onDirect(ByteBuf byteBuf) {
            // We don't allow any direct packets so ignore
        }
    }
}
