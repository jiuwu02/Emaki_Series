package emaki.jiuwu.craft.skills.provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.skills.api.SkillSourceProvider;
import emaki.jiuwu.craft.skills.api.SkillSourceRegistration;

/** Owner-and-provider-id scoped registry for external skill sources. */
public final class SkillSourceRegistry implements Listener, AutoCloseable {

    private final Map<ProviderKey, RegisteredProvider> registrations = new LinkedHashMap<>();
    private long registrationGeneration;

    public synchronized SkillSourceRegistration register(Plugin owner, SkillSourceProvider provider) {
        if (owner == null || !owner.isEnabled() || provider == null) {
            return SkillSourceRegistration.noop();
        }
        String id;
        try {
            id = normalize(provider.id());
        } catch (RuntimeException | LinkageError exception) {
            return SkillSourceRegistration.noop();
        }
        if (id.isBlank()) {
            return SkillSourceRegistration.noop();
        }
        ProviderKey key = new ProviderKey(owner, id);
        long generation = ++registrationGeneration;
        registrations.put(key, new RegisteredProvider(provider, generation));
        return new RegistrationHandle(this, key, provider, generation);
    }

    public List<RegisteredSource> all() {
        List<Map.Entry<ProviderKey, RegisteredProvider>> snapshot;
        synchronized (this) {
            registrations.keySet().removeIf(key -> !key.owner().isEnabled());
            snapshot = new ArrayList<>(registrations.entrySet());
        }
        return snapshot.stream()
                .sorted(Comparator.comparingInt(entry -> priority(entry.getValue().provider())))
                .map(entry -> new RegisteredSource(entry.getKey().owner(), entry.getKey().id(),
                        entry.getValue().provider()))
                .toList();
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

    private synchronized void unregister(ProviderKey key, SkillSourceProvider provider, long generation) {
        RegisteredProvider registered = registrations.get(key);
        if (registered == null
                || registered.generation() != generation
                || registered.provider() != provider) {
            return;
        }
        registrations.remove(key);
    }

    private static int priority(SkillSourceProvider provider) {
        try {
            return provider.priority();
        } catch (RuntimeException | LinkageError exception) {
            return 100;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record RegisteredSource(Plugin owner, String id, SkillSourceProvider provider) {
    }

    private record ProviderKey(Plugin owner, String id) {
    }

    private record RegisteredProvider(SkillSourceProvider provider, long generation) {
    }

    private static final class RegistrationHandle implements SkillSourceRegistration {
        private final SkillSourceRegistry registry;
        private final ProviderKey key;
        private final SkillSourceProvider provider;
        private final long generation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RegistrationHandle(SkillSourceRegistry registry,
                ProviderKey key,
                SkillSourceProvider provider,
                long generation) {
            this.registry = registry;
            this.key = key;
            this.provider = provider;
            this.generation = generation;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                registry.unregister(key, provider, generation);
            }
        }
    }
}
