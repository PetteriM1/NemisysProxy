package com.nukkitx.network.raknet;

import com.nukkitx.network.raknet.pipeline.*;
import com.nukkitx.network.util.Bootstraps;
import com.nukkitx.network.util.DisconnectReason;
import com.nukkitx.network.util.EventLoops;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.itxtech.nemisys.Server;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static com.nukkitx.network.raknet.RakNetConstants.*;

@ParametersAreNonnullByDefault
public class RakNetServer extends RakNet {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(RakNetServer.class);

    private final ConcurrentMap<InetAddress, Long> blockAddresses = new ConcurrentHashMap<>();
    final ConcurrentMap<InetSocketAddress, RakNetServerSession> sessionsByAddress = new ConcurrentHashMap<>();
    final ConcurrentMap<InetAddress, Integer> sessionCount = new ConcurrentHashMap<>();
    private final ConcurrentMap<InetAddress, Integer> violationCount = new ConcurrentHashMap<>();
    public final ConcurrentMap<InetAddress, Integer> packetsPerSecond = new ConcurrentHashMap<>();

    private final InetSocketAddress bindAddress;
    private final int bindThreads;

    private final ServerChannelInitializer initializer = new ServerChannelInitializer();
    private final ServerMessageHandler messageHandler = new ServerMessageHandler(this);
    private final ServerDatagramHandler serverDatagramHandler = new ServerDatagramHandler(this);
    private final RakExceptionHandler exceptionHandler = new RakExceptionHandler(this);

    private volatile RakNetServerListener listener = null;
    private volatile Channel channel;

    public RakNetServer(InetSocketAddress bindAddress) {
        this(bindAddress, 1);
    }

    public RakNetServer(InetSocketAddress bindAddress, int bindThreads) {
        this(bindAddress, bindThreads, EventLoops.commonGroup());
    }

    public RakNetServer(InetSocketAddress bindAddress, int bindThreads, EventLoopGroup eventLoopGroup) {
        this(bindAddress, bindThreads, eventLoopGroup, false);
    }

    public RakNetServer(InetSocketAddress bindAddress, int bindThreads, EventLoopGroup eventLoopGroup, boolean useProxyProtocol) {
        super(eventLoopGroup);
        this.bindThreads = bindThreads;
        this.bindAddress = bindAddress;
        Server.getInstance().getScheduler().scheduleRepeatingTask(() -> {
            packetsPerSecond.clear();
            violationCount.clear();
        }, 20, true);
    }

    @Override
    protected CompletableFuture<Void> bindInternal() {
        int bindThreads = Bootstraps.isReusePortAvailable() ? this.bindThreads : 1;
        ChannelFuture[] channelFutures = new ChannelFuture[bindThreads];

        for (int i = 0; i < bindThreads; i++) {
            channelFutures[i] = this.bootstrap.handler(this.initializer).bind(this.bindAddress);
        }
        return Bootstraps.allOf(channelFutures);
    }

    public void send(InetSocketAddress address, ByteBuf buffer) {
        if (this.channel != null) {
            this.channel.writeAndFlush(new DatagramPacket(buffer, address));
        }
    }

    @Override
    public void close(boolean force) {
        super.close(force);
        for (RakNetServerSession session : this.sessionsByAddress.values()) {
            session.disconnect(DisconnectReason.SHUTTING_DOWN);
        }
        if (this.channel != null) {
            this.channel.close().syncUninterruptibly();
        }
    }

    @Override
    protected void onTick() {
        final long curTime = System.currentTimeMillis();
        for (RakNetServerSession session : this.sessionsByAddress.values()) {
            session.eventLoop.execute(() -> session.onTick(curTime));
        }
        Iterator<Long> blockedAddresses = this.blockAddresses.values().iterator();
        long timeout;
        while (blockedAddresses.hasNext()) {
            timeout = blockedAddresses.next();
            if (timeout > 0 && timeout < curTime) {
                blockedAddresses.remove();
            }
        }
    }

    public void onOpenConnectionRequest1(ChannelHandlerContext ctx, DatagramPacket packet) {
        ByteBuf buffer = packet.content();
        if (!buffer.isReadable(16)) {
            Server.getInstance().getLogger().info(packet.sender() + " ocr1 not readable");
            return;
        }

        // We want to do as many checks as possible before creating a session so memory is not wasted.
        if (!RakNetUtils.verifyUnconnectedMagic(buffer)) {
            Server.getInstance().getLogger().info(packet.sender() + " ocr1 unverified magic");
            return;
        }

        int protocolVersion = buffer.readUnsignedByte();
        InetAddress address = packet.sender().getAddress();
        int mtu = buffer.readableBytes() + 18 + (address instanceof Inet6Address ? 40 : 20) + UDP_HEADER_SIZE; // 1 (Packet ID), 16 (Magic), 1 (Protocol Version), 20/40 (IP Header)

        RakNetServerSession session = this.sessionsByAddress.get(packet.sender());

        if (session != null && session.getState() == RakNetState.CONNECTED) {
            this.sendAlreadyConnected(ctx, packet.sender());
        } else if (protocolVersion < 9) {
            this.sendIncompatibleProtocolVersion(ctx, packet.sender());
        } else if (this.listener != null && !this.listener.onConnectionRequest(packet.sender(), packet.sender())) {
            this.sendConnectionBanned(ctx, packet.sender(), true);
        } else if (session == null) {
            Integer sessions = this.sessionCount.get(address);
            if (sessions == null) {
                this.sessionCount.put(address, 1);
            } else {
                if (sessions > Server.maxSessions) {
                    Server.getInstance().getLogger().info(packet.sender() + " ocr1 too many sessions");
                    if (Server.customStuff) {
                        Integer violations = this.violationCount.get(address);
                        if (violations == null) {
                            this.violationCount.put(address, 1);
                            this.sendConnectionBanned(ctx, packet.sender(), false);
                        } else {
                            if (violations > 5) {
                                Server.getInstance().getLogger().warning("[Temp IP-Ban] Too many session limit violations from " + address);
                                this.block(address, 120, TimeUnit.SECONDS);
                                return;
                            }
                            this.violationCount.put(address, violations + 1);
                        }
                    }
                    return;
                }
                this.sessionCount.put(address, sessions + 1);
            }

            // Passed all checks. Now create the session and send the first reply.
            session = new RakNetServerSession(this, packet.sender(), ctx.channel(),
                    ctx.channel().eventLoop().next(), mtu, protocolVersion);
            if (this.sessionsByAddress.putIfAbsent(packet.sender(), session) == null) {
                session.setState(RakNetState.INITIALIZING);
                session.sendOpenConnectionReply1();
                if (listener != null) {
                    listener.onSessionCreation(session);
                } else {
                    Server.getInstance().getLogger().warning("Unable to create session for " + packet.sender() + ": listener is null");
                }
            }
        } else {
            session.setMtu(mtu);
            session.sendOpenConnectionReply1(); // Probably a packet loss occurred, send the reply again
            Server.getInstance().getLogger().info(packet.sender() + " new connection reply");
        }
    }

    public void block(InetAddress address) {
        Objects.requireNonNull(address, "address");
        this.blockAddresses.put(address, -1L);
    }

    public void block(InetAddress address, long timeout, TimeUnit timeUnit) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(address, "timeUnit");
        this.blockAddresses.put(address, System.currentTimeMillis() + timeUnit.toMillis(timeout));
    }

    public boolean unblock(InetAddress address) {
        Objects.requireNonNull(address, "address");
        return this.blockAddresses.remove(address) != null;
    }

    public boolean isBlocked(InetAddress address) {
        return this.blockAddresses.containsKey(address);
    }

    public int getSessionCount() {
        return this.sessionsByAddress.size();
    }

    @Nullable
    public RakNetServerSession getSession(InetSocketAddress address) {
        return this.sessionsByAddress.get(address);
    }

    @Override
    public InetSocketAddress getBindAddress() {
        return this.bindAddress;
    }

    public RakNetServerListener getListener() {
        return listener;
    }

    public void setListener(RakNetServerListener listener) {
        this.listener = listener;
    }

    /*
     * Packet Dispatchers
     */

    private void sendAlreadyConnected(ChannelHandlerContext ctx, InetSocketAddress recipient) {
        ByteBuf buffer = ctx.alloc().ioBuffer(25, 25);
        buffer.writeByte(ID_ALREADY_CONNECTED);
        RakNetUtils.writeUnconnectedMagic(buffer);
        buffer.writeLong(this.guid);
        ctx.writeAndFlush(new DatagramPacket(buffer, recipient));
        Server.getInstance().getLogger().info(recipient + " already connected");
    }

    private void sendConnectionBanned(ChannelHandlerContext ctx, InetSocketAddress recipient, boolean log) {
        ByteBuf buffer = ctx.alloc().ioBuffer(25, 25);
        buffer.writeByte(ID_CONNECTION_BANNED);
        RakNetUtils.writeUnconnectedMagic(buffer);
        buffer.writeLong(this.guid);
        ctx.writeAndFlush(new DatagramPacket(buffer, recipient));
        if (log) Server.getInstance().getLogger().info(recipient + " connection banned");
    }

    private void sendIncompatibleProtocolVersion(ChannelHandlerContext ctx, InetSocketAddress recipient) {
        ByteBuf buffer = ctx.alloc().ioBuffer(26, 26);
        buffer.writeByte(ID_INCOMPATIBLE_PROTOCOL_VERSION);
        buffer.writeByte(RAKNET_PROTOCOL_VERSION);
        RakNetUtils.writeUnconnectedMagic(buffer);
        buffer.writeLong(this.guid);
        ctx.writeAndFlush(new DatagramPacket(buffer, recipient));
        Server.getInstance().getLogger().info(recipient + " incompatible protocol");
    }

    @ChannelHandler.Sharable
    private class ServerChannelInitializer extends ChannelInitializer<Channel> {

        @Override
        protected void initChannel(Channel channel) throws Exception {
            ChannelPipeline pipeline = channel.pipeline();
            pipeline.addLast(RakOutboundHandler.NAME, new RakOutboundHandler(RakNetServer.this));
            pipeline.addLast(ServerMessageHandler.NAME, RakNetServer.this.messageHandler);
            pipeline.addLast(ServerDatagramHandler.NAME, RakNetServer.this.serverDatagramHandler);
            pipeline.addLast(RakExceptionHandler.NAME, RakNetServer.this.exceptionHandler);
            RakNetServer.this.channel = channel;
        }
    }
}
