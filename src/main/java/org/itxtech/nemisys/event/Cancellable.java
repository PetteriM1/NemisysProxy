package org.itxtech.nemisys.event;

/**
 * Created by Nukkit Team.
 */
public interface Cancellable {

    boolean isCancelled();

    void setCancelled(boolean forceCancel);

    void setCancelled();
}
