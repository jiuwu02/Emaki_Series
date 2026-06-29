package emaki.jiuwu.craft.corelib.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;

public final class EmakiEventBus {

    private static final Logger LOGGER = Logger.getLogger(EmakiEventBus.class.getName());

    private final Map<Class<?>, CopyOnWriteArrayList<EventSubscription<?>>> subscribers = new ConcurrentHashMap<>();

    public EmakiEventBus() {
    }

    public <T extends EmakiEvent> Subscription subscribe(Class<T> eventType, Consumer<T> handler) {
        return subscribe(null, eventType, handler);
    }

    public <T extends EmakiEvent> Subscription subscribe(Plugin owner, Class<T> eventType, Consumer<T> handler) {
        if (eventType == null || handler == null) {
            return NoopSubscription.INSTANCE;
        }
        EventSubscription<T> subscription = new EventSubscription<>(owner, eventType, handler);
        subscribers.computeIfAbsent(eventType, _ -> new CopyOnWriteArrayList<>()).add(subscription);
        return subscription;
    }

    @SuppressWarnings("unchecked")
    public <T extends EmakiEvent> void publish(T event) {
        if (event == null) {
            return;
        }
        List<EventSubscription<?>> handlers = subscriptionsFor(event.getClass());
        if (handlers.isEmpty()) {
            return;
        }
        for (EventSubscription<?> subscription : handlers) {
            if (!subscription.active()) {
                continue;
            }
            try {
                ((EventSubscription<T>) subscription).dispatch(event);
            } catch (Exception exception) {
                LOGGER.warning("Event handler threw exception for " + event.eventType() + ": " + exception.getMessage());
            }
        }
    }

    public void unsubscribeAll(Class<? extends EmakiEvent> eventType) {
        if (eventType == null) {
            return;
        }
        CopyOnWriteArrayList<EventSubscription<?>> removed = subscribers.remove(eventType);
        if (removed == null) {
            return;
        }
        for (EventSubscription<?> subscription : removed) {
            subscription.markInactive();
        }
    }

    public void unsubscribeAll(Plugin owner) {
        if (owner == null) {
            return;
        }
        for (CopyOnWriteArrayList<EventSubscription<?>> subscriptions : subscribers.values()) {
            for (EventSubscription<?> subscription : subscriptions) {
                if (owner.equals(subscription.owner())) {
                    subscription.unsubscribe();
                }
            }
        }
    }

    public void clear() {
        for (CopyOnWriteArrayList<EventSubscription<?>> subscriptions : subscribers.values()) {
            for (EventSubscription<?> subscription : subscriptions) {
                subscription.markInactive();
            }
        }
        subscribers.clear();
    }

    private List<EventSubscription<?>> subscriptionsFor(Class<?> eventType) {
        List<EventSubscription<?>> result = new ArrayList<>();
        for (Map.Entry<Class<?>, CopyOnWriteArrayList<EventSubscription<?>>> entry : subscribers.entrySet()) {
            Class<?> subscribedType = entry.getKey();
            if (!subscribedType.isAssignableFrom(eventType)) {
                continue;
            }
            result.addAll(entry.getValue());
        }
        return result;
    }

    public interface Subscription {
        Class<? extends EmakiEvent> eventType();

        Plugin owner();

        boolean active();

        void unsubscribe();
    }

    private final class EventSubscription<T extends EmakiEvent> implements Subscription {

        private final Plugin owner;
        private final Class<T> eventType;
        private final Consumer<T> handler;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private EventSubscription(Plugin owner, Class<T> eventType, Consumer<T> handler) {
            this.owner = owner;
            this.eventType = eventType;
            this.handler = handler;
        }

        @Override
        public Class<T> eventType() {
            return eventType;
        }

        @Override
        public Plugin owner() {
            return owner;
        }

        @Override
        public boolean active() {
            return active.get();
        }

        @Override
        public void unsubscribe() {
            if (!active.compareAndSet(true, false)) {
                return;
            }
            CopyOnWriteArrayList<EventSubscription<?>> subscriptions = subscribers.get(eventType);
            if (subscriptions != null) {
                subscriptions.remove(this);
                if (subscriptions.isEmpty()) {
                    subscribers.remove(eventType, subscriptions);
                }
            }
        }

        private void markInactive() {
            active.set(false);
        }

        private void dispatch(T event) {
            handler.accept(event);
        }
    }

    private enum NoopSubscription implements Subscription {
        INSTANCE;

        @Override
        public Class<? extends EmakiEvent> eventType() {
            return EmakiEvent.class;
        }

        @Override
        public Plugin owner() {
            return null;
        }

        @Override
        public boolean active() {
            return false;
        }

        @Override
        public void unsubscribe() {
        }
    }
}
