package org.itxtech.nemisys.event.client;

import org.itxtech.nemisys.Client;
import org.itxtech.nemisys.event.Cancellable;
import org.itxtech.nemisys.event.HandlerList;

/**
 * @author PeratX
 */
public class PluginMsgRecvEvent extends ClientEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final String channel;
    private final byte[] data;

    public PluginMsgRecvEvent(Client client, String channel, byte[] data) {
        super(client);
        this.channel = channel;
        this.data = data;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public String getChannel() {
        return channel;
    }

    public byte[] getData() {
        return data;
    }
}
