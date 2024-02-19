package org.itxtech.nemisys.plugin;

import org.itxtech.nemisys.event.Event;
import org.itxtech.nemisys.event.Listener;
import org.itxtech.nemisys.utils.EventException;

/**
 * @author iNevet
 * Nukkit Project
 */
public interface EventExecutor {

    void execute(Listener listener, Event event) throws EventException;
}
