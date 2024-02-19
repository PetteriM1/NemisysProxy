package org.itxtech.nemisys.plugin;

import org.itxtech.nemisys.Server;
import org.itxtech.nemisys.event.Event;
import org.itxtech.nemisys.event.Listener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class MethodEventExecutor implements EventExecutor {

    private final Method method;

    public MethodEventExecutor(Method method) {
        this.method = method;
    }

    @Override
    public void execute(Listener listener, Event event) {
        try {
            method.invoke(listener, event);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Server.getInstance().getLogger().logException(e);
        }
    }

    public Method getMethod() {
        return method;
    }
}
