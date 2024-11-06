package org.itxtech.nemisys.plugin;

import org.itxtech.nemisys.event.Event;
import org.itxtech.nemisys.event.Listener;
import org.itxtech.nemisys.utils.EventException;

import java.util.function.BiConsumer;

public class LambdaEventExecutor implements EventExecutor {

    private final Class<? extends Event> clazz;
    private final BiConsumer<Listener, Event> callback;

    public LambdaEventExecutor(Class<? extends Event> clazz, BiConsumer<Listener, Event> callback) {
        this.clazz = clazz;
        this.callback = callback;
    }

    @Override
    public void execute(Listener listener, Event event) throws EventException {
        if (clazz.isAssignableFrom(event.getClass())) {
            try {
                this.callback.accept(listener, event);
            } catch (Throwable t) {
                throw new EventException(t);
            }
        }
    }
}
