package emaki.jiuwu.craft.corelib.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class EmakiEventBus {

    private final Map<Class<?>, List<Consumer<?>>> subscribers = new ConcurrentHashMap<>();

    public EmakiEventBus() {
    }

    @SuppressWarnings("unchecked")
    public <T extends EmakiEvent> void subscribe(Class<T> eventType, Consumer<T> handler) {
        if (eventType == null || handler == null) {
            return;
        }
        subscribers.computeIfAbsent(eventType, _ -> new CopyOnWriteArrayList<>())
                .add((Consumer<?>) handler);
    }

    @SuppressWarnings("unchecked")
    public <T extends EmakiEvent> void publish(T event) {
        if (event == null) {
            return;
        }
        List<Consumer<?>> handlers = subscribers.get(event.getClass());
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        for (Consumer<?> handler : handlers) {
            try {
                ((Consumer<T>) handler).accept(event);
            } catch (Exception exception) {
                java.util.logging.Logger.getLogger(EmakiEventBus.class.getName())
                        .warning("Event handler threw exception for " + event.eventType() + ": " + exception.getMessage());
            }
        }
    }

    public void unsubscribeAll(Class<? extends EmakiEvent> eventType) {
        if (eventType != null) {
            subscribers.remove(eventType);
        }
    }

    public void clear() {
        subscribers.clear();
    }
}
