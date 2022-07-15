package com.nukkitx.network.raknet;

import com.nukkitx.network.NetworkUtils;
import com.nukkitx.network.util.DisconnectReason;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import org.itxtech.nemisys.Server;

import javax.annotation.ParametersAreNonnullByDefault;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static com.nukkitx.network.raknet.RakNetConstants.*;

@ParametersAreNonnullByDefault
public class RakNetServerSession extends RakNetSession {

    private final RakNetServer rakNet;

    RakNetServerSession(RakNetServer rakNet, InetSocketAddress remoteAddress, Channel channel, EventLoop eventLoop, int mtu,
                        int protocolVersion) {
        super(remoteAddress, channel, eventLoop, mtu, protocolVersion);
        this.rakNet = rakNet;
    }

    @Override
    protected void onPacket(ByteBuf buffer) {
        short packetId = buffer.readUnsignedByte();

        switch (packetId) {
            case ID_OPEN_CONNECTION_REQUEST_2:
                this.onOpenConnectionRequest2(buffer);
                break;
            case ID_CONNECTION_REQUEST:
                this.onConnectionRequest(buffer);
                break;
            case ID_NEW_INCOMING_CONNECTION:
                this.onNewIncomingConnection();
                break;
        }
    }

    @Override
    protected void onClose() {
        InetAddress address = this.address.getAddress();
        Integer sessions = this.rakNet.sessionCount.get(address);
        if (sessions == null) {
            Server.getInstance().getLogger().warning("Session was not found in session counts: " + this.address);
        } else {
            if (sessions < 2) {
                this.rakNet.sessionCount.remove(address);
            } else {
                this.rakNet.sessionCount.put(address, sessions - 1);
            }
        }
        if (!this.rakNet.sessionsByAddress.remove(this.address, this)) {
            throw new IllegalStateException("Session was not found in session map: " + this.address);
        }
    }

    @Override
    public RakNet getRakNet() {
        return this.rakNet;
    }

    private void onOpenConnectionRequest2(ByteBuf buffer) {
        if (this.getState() != RakNetState.INITIALIZING) {
            Server.getInstance().getLogger().info(this.address + " ocr2 while not initializing (state=" + this.getState() + ')');
            if (this.getState() != RakNetState.INITIALIZED) { // Already INITIALIZED == probably a packet loss occurred
                return;
            }
        }

        if (!RakNetUtils.verifyUnconnectedMagic(buffer)) {
            Server.getInstance().getLogger().info(this.address + " ocr2 unverified magic");
            return;
        }

        NetworkUtils.readAddress(buffer);

        int mtu = buffer.readUnsignedShort();
        this.setMtu(mtu);
        this.guid = buffer.readLong();

        if (this.getState() == RakNetState.INITIALIZING) {
            // We can now accept RakNet datagrams.
            this.initialize();
        }

        sendOpenConnectionReply2();
        this.setState(RakNetState.INITIALIZED);
    }

    private void onConnectionRequest(ByteBuf buffer) {
        if (this.getState() == RakNetState.CONNECTING) {
            Server.getInstance().getLogger().info(this.address + " connection request but already connecting");
        }

        long guid = buffer.readLong();
        long time = buffer.readLong();
        boolean security = buffer.readBoolean();

        if (this.guid != guid || security) {
            this.sendConnectionFailure(ID_CONNECTION_REQUEST_FAILED);
            this.close(DisconnectReason.CONNECTION_REQUEST_FAILED);
            return;
        }

        this.setState(RakNetState.CONNECTING);

        this.sendConnectionRequestAccepted(time);
    }

    private void onNewIncomingConnection() {
        if (this.getState() != RakNetState.CONNECTING) {
            Server.getInstance().getLogger().info(this.address + " incoming connection while not connecting (state=" + this.getState() + ')');
            return;
        }

        this.setState(RakNetState.CONNECTED);
    }

    void sendOpenConnectionReply1() {
        ByteBuf buffer = this.allocateBuffer(28);

        buffer.writeByte(ID_OPEN_CONNECTION_REPLY_1);
        RakNetUtils.writeUnconnectedMagic(buffer);
        buffer.writeLong(this.rakNet.guid);
        buffer.writeBoolean(false); // Security
        buffer.writeShort(this.getMtu());

        this.sendDirect(buffer);
    }

    private void sendOpenConnectionReply2() {
        ByteBuf buffer = this.allocateBuffer(31);

        buffer.writeByte(ID_OPEN_CONNECTION_REPLY_2);
        RakNetUtils.writeUnconnectedMagic(buffer);
        buffer.writeLong(this.rakNet.guid);
        NetworkUtils.writeAddress(buffer, this.address);
        buffer.writeShort(this.getMtu());
        buffer.writeBoolean(false); // Security

        this.sendDirect(buffer);
    }

    private void sendConnectionFailure(short id) {
        ByteBuf buffer = this.allocateBuffer(21);
        buffer.writeByte(id);
        RakNetUtils.writeUnconnectedMagic(buffer);
        buffer.writeLong(this.rakNet.guid);

        this.sendDirect(buffer);
    }

    private void sendConnectionRequestAccepted(long time) {
        boolean ipv6 = this.isIpv6Session();
        ByteBuf buffer = this.allocateBuffer(ipv6 ? 628 : 166);

        buffer.writeByte(ID_CONNECTION_REQUEST_ACCEPTED);
        NetworkUtils.writeAddress(buffer, this.address);
        buffer.writeShort(0); // System index

        for (InetSocketAddress socketAddress : ipv6 ? LOCAL_IP_ADDRESSES_V6 : LOCAL_IP_ADDRESSES_V4) {
            NetworkUtils.writeAddress(buffer, socketAddress);
        }

        buffer.writeLong(time);
        buffer.writeLong(System.currentTimeMillis());

        this.send(buffer, RakNetPriority.IMMEDIATE, RakNetReliability.RELIABLE);
    }
}
