package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.Collections;
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

import emaki.jiuwu.craft.item.api.preview.ItemLayerPreviewProvider;
import emaki.jiuwu.craft.item.api.preview.ItemLayerPreviewRegistration;

public final class EmakiItemLayerPreviewRegistry implements Listener, AutoCloseable {

    private final Map<String, RegisteredProvider> providers = new LinkedHashMap<>();
    private long registrationGeneration;

    public synchronized ItemLayerPreviewRegistration register(
            Plugin owner,
            ItemLayerPreviewProvider provider) {
        if (owner == null || provider == null) {
            return ItemLayerPreviewRegistration.noop();
        }
        String id = normalize(provider.id());
        if (id.isBlank()) {
            return ItemLayerPreviewRegistration.noop();
        }
        long generation = ++registrationGeneration;
        providers.put(id, new RegisteredProvider(owner, provider, provider.order(), generation));
        return new RegistrationHandle(this, id, generation);
    }

    public synchronized Map<String, ItemLayerPreviewProvider> providersById() {
        providers.entrySet().removeIf(entry -> !entry.getValue().owner().isEnabled());
        List<Map.Entry<String, RegisteredProvider>> ordered = new ArrayList<>(providers.entrySet());
        ordered.sort(Comparator
                .comparingInt((Map.Entry<String, RegisteredProvider> entry) -> entry.getValue().order())
                .thenComparing(Map.Entry::getKey));
        Map<String, ItemLayerPreviewProvider> result = new LinkedHashMap<>();
        for (Map.Entry<String, RegisteredProvider> entry : ordered) {
            result.put(entry.getKey(), entry.getValue().provider());
        }
        return Collections.unmodifiableMap(result);
    }

    public synchronized void unregisterOwner(Plugin owner) {
        if (owner == null) {
            return;
        }
        providers.entrySet().removeIf(entry -> entry.getValue().owner() == owner);
    }

    public synchronized void clear() {
        providers.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        unregisterOwner(event.getPlugin());
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        clear();
    }

    private synchronized void unregister(String id, long generation) {
        RegisteredProvider registered = providers.get(id);
        if (registered == null || registered.generation() != generation) {
            return;
        }
        providers.remove(id);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record RegisteredProvider(
            Plugin owner,
            ItemLayerPreviewProvider provider,
            int order,
            long generation) {
    }

    private static final class RegistrationHandle implements ItemLayerPreviewRegistration {

        private final EmakiItemLayerPreviewRegistry registry;
        private final String id;
        private final long generation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RegistrationHandle(EmakiItemLayerPreviewRegistry registry, String id, long generation) {
            this.registry = registry;
            this.id = id;
            this.generation = generation;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                registry.unregister(id, generation);
            }
        }
    }
}
