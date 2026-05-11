package emaki.jiuwu.craft.corelib.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Lightweight cross-module event bus for the Emaki plugin suite.
 * <p>
 * This is NOT a Bukkit Event system. It provides simple publish/subscribe
 * semantics for internal cross-module communication without requiring
 * hard dependencies between modules.
 * <p>
 * Thread-safe: subscriptions and publications can happen from any thread.
 * Handlers are invoked synchronously on the publishing thread.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Subscribe
 * eventBus.subscribe(StrengthenSuccessEvent.class, event -> {
 *     // handle event
 * });
 *
 * // Publish
 * eventBus.publish(new StrengthenSuccessEvent(player, star));
 * }</pre>
 */
public final class EmakiEventBus {

    private final Map<Class<?>, List<Consumer<?>>> subscribers = new ConcurrentHashMap<>();

    public EmakiEventBus() {
    }

    /**
     * Subscribe to events of a specific type.
     *
     * @param eventType the event class to listen for
     * @param handler   the handler to invoke when the event is published
     * @param <T>       the event type
     */
    @SuppressWarnings("unchecked")
    public <T extends EmakiEvent> void subscribe(Class<T> eventType, Consumer<T> handler) {
        if (eventType == null || handler == null) {
            return;
        }
        subscribers.computeIfAbsent(eventType, _ -> new CopyOnWriteArrayList<>())
                .add((Consumer<?>) handler);
    }

    /**
     * Publish an event to all registered subscribers.
     *
     * @param event the event to publish
     * @param <T>   the event type
     */
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
                // Swallow handler exceptions to prevent one bad subscriber from breaking others
                java.util.logging.Logger.getLogger(EmakiEventBus.class.getName())
                        .warning("Event handler threw exception for " + event.eventType() + ": " + exception.getMessage());
            }
        }
    }

    /**
     * Remove all subscribers for a specific event type.
     */
    public void unsubscribeAll(Class<? extends EmakiEvent> eventType) {
        if (eventType != null) {
            subscribers.remove(eventType);
        }
    }

    /**
     * Remove all subscribers.
     */
    public void clear() {
        subscribers.clear();
    }
}
