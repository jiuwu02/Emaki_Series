package emaki.jiuwu.craft.level.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.level.api.ExpSourceContext;
import emaki.jiuwu.craft.level.api.ExpSourceGrant;
import emaki.jiuwu.craft.level.api.ExpSourceProvider;
import emaki.jiuwu.craft.level.api.ExpSourceRegistration;

public final class ExpSourceProviderRegistry implements Listener, AutoCloseable {

    private final Plugin plugin;
    private final Map<ProviderKey, RegisteredProvider> registrations = new LinkedHashMap<>();
    private long registrationGeneration;

    public ExpSourceProviderRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    public synchronized ExpSourceRegistration register(Plugin owner, ExpSourceProvider provider) {
        if (owner == null || !owner.isEnabled() || provider == null) {
            return ExpSourceRegistration.noop();
        }
        String id;
        try {
            id = normalize(provider.id());
        } catch (RuntimeException | LinkageError exception) {
            logProviderFailure("<unknown>", exception);
            return ExpSourceRegistration.noop();
        }
        if (id.isBlank()) {
            return ExpSourceRegistration.noop();
        }
        ProviderKey key = new ProviderKey(owner, id);
        long generation = ++registrationGeneration;
        registrations.put(key, new RegisteredProvider(provider, generation));
        return new RegistrationHandle(this, key, provider, generation);
    }

    public void dispatch(ExpSourceContext context, Consumer<ExpSourceGrant> grantConsumer) {
        if (context == null || grantConsumer == null) {
            return;
        }
        List<Map.Entry<ProviderKey, RegisteredProvider>> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(registrations.entrySet());
        }
        for (Map.Entry<ProviderKey, RegisteredProvider> entry : snapshot) {
            if (!entry.getKey().owner().isEnabled()) {
                unregisterOwner(entry.getKey().owner());
                continue;
            }
            Iterable<ExpSourceGrant> grants;
            try {
                grants = entry.getValue().provider().provide(context);
            } catch (RuntimeException | LinkageError exception) {
                logProviderFailure(entry.getKey().id(), exception);
                continue;
            }
            if (grants == null) {
                continue;
            }
            try {
                for (ExpSourceGrant grant : grants) {
                    if (grant != null) {
                        grantConsumer.accept(grant);
                    }
                }
            } catch (RuntimeException | LinkageError exception) {
                logProviderFailure(entry.getKey().id(), exception);
            }
        }
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

    private synchronized void unregister(ProviderKey key, ExpSourceProvider provider, long generation) {
        RegisteredProvider registered = registrations.get(key);
        if (registered == null
                || registered.generation() != generation
                || registered.provider() != provider) {
            return;
        }
        registrations.remove(key);
    }

    private void logProviderFailure(String id, Throwable throwable) {
        if (plugin != null) {
            plugin.getLogger().warning("Experience source provider '" + id + "' failed: " + throwable.getMessage());
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ProviderKey(Plugin owner, String id) {
    }

    private record RegisteredProvider(ExpSourceProvider provider, long generation) {
    }

    private static final class RegistrationHandle implements ExpSourceRegistration {

        private final ExpSourceProviderRegistry registry;
        private final ProviderKey key;
        private final ExpSourceProvider provider;
        private final long generation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RegistrationHandle(ExpSourceProviderRegistry registry,
                ProviderKey key,
                ExpSourceProvider provider,
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
