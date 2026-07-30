package emaki.jiuwu.craft.codex.advancement.trigger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.codex.api.AdvancementTrigger;
import emaki.jiuwu.craft.codex.api.AdvancementTriggerContext;
import emaki.jiuwu.craft.codex.api.AdvancementTriggerRegistration;

/** Owner-scoped registry for external gameplay-to-advancement trigger providers. */
public final class AdvancementTriggerRegistry implements Listener, AutoCloseable {

    private final Plugin plugin;
    private final Map<TriggerKey, RegisteredTrigger> registrations = new LinkedHashMap<>();
    private long generation;

    public AdvancementTriggerRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    public synchronized AdvancementTriggerRegistration register(Plugin owner, AdvancementTrigger trigger) {
        if (owner == null || !owner.isEnabled() || trigger == null) {
            return AdvancementTriggerRegistration.noop();
        }
        String id;
        try {
            id = normalize(trigger.id());
        } catch (RuntimeException | LinkageError exception) {
            return AdvancementTriggerRegistration.noop();
        }
        if (id.isBlank()) {
            return AdvancementTriggerRegistration.noop();
        }
        TriggerKey key = new TriggerKey(owner, id);
        long currentGeneration = ++generation;
        registrations.put(key, new RegisteredTrigger(trigger, currentGeneration));
        return new RegistrationHandle(this, key, trigger, currentGeneration);
    }

    /** Evaluates active providers once and returns de-duplicated advancement ids in provider order. */
    public Set<String> dispatch(AdvancementTriggerContext context) {
        if (context == null) {
            return Set.of();
        }
        List<Map.Entry<TriggerKey, RegisteredTrigger>> snapshot;
        synchronized (this) {
            registrations.keySet().removeIf(key -> !key.owner().isEnabled());
            snapshot = new ArrayList<>(registrations.entrySet());
        }
        snapshot.sort(Comparator.comparingInt(entry -> priority(entry.getValue().trigger())));
        Set<String> result = new LinkedHashSet<>();
        for (Map.Entry<TriggerKey, RegisteredTrigger> entry : snapshot) {
            try {
                var ids = entry.getValue().trigger().advancements(context);
                if (ids == null) {
                    continue;
                }
                for (String id : ids) {
                    if (id != null && !id.isBlank()) {
                        result.add(id.trim());
                    }
                }
            } catch (RuntimeException | LinkageError exception) {
                if (plugin != null) {
                    plugin.getLogger().warning("Advancement trigger provider '" + entry.getKey().id()
                            + "' failed: " + exception.getMessage());
                }
            }
        }
        return java.util.Collections.unmodifiableSet(result);
    }

    public synchronized void unregisterOwner(Plugin owner) {
        if (owner != null) {
            registrations.keySet().removeIf(key -> key.owner() == owner);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        unregisterOwner(event.getPlugin());
    }

    @Override
    public synchronized void close() {
        HandlerList.unregisterAll(this);
        registrations.clear();
    }

    private synchronized void unregister(TriggerKey key, AdvancementTrigger trigger, long targetGeneration) {
        RegisteredTrigger registered = registrations.get(key);
        if (registered == null || registered.generation() != targetGeneration
                || registered.trigger() != trigger) {
            return;
        }
        registrations.remove(key);
    }

    private static int priority(AdvancementTrigger trigger) {
        try {
            return trigger.priority();
        } catch (RuntimeException | LinkageError exception) {
            return 100;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record TriggerKey(Plugin owner, String id) { }
    private record RegisteredTrigger(AdvancementTrigger trigger, long generation) { }

    private static final class RegistrationHandle implements AdvancementTriggerRegistration {
        private final AdvancementTriggerRegistry registry;
        private final TriggerKey key;
        private final AdvancementTrigger trigger;
        private final long generation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RegistrationHandle(AdvancementTriggerRegistry registry,
                TriggerKey key,
                AdvancementTrigger trigger,
                long generation) {
            this.registry = registry;
            this.key = key;
            this.trigger = trigger;
            this.generation = generation;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                registry.unregister(key, trigger, generation);
            }
        }
    }
}
