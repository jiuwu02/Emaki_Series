package emaki.jiuwu.craft.attribute.service;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.attribute.api.extension.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.api.extension.ContributionProviderRegistration;

public final class ContributionProviderRegistrationRegistry implements Listener, AutoCloseable {

    private final AttributeServiceFacade attributeService;
    private final Map<String, RegisteredProvider> registrations = new LinkedHashMap<>();
    private long registrationGeneration;

    public ContributionProviderRegistrationRegistry(AttributeServiceFacade attributeService) {
        this.attributeService = attributeService;
    }

    public synchronized ContributionProviderRegistration register(Plugin owner,
            AttributeContributionProvider provider) {
        if (attributeService == null || owner == null || !owner.isEnabled() || provider == null) {
            return ContributionProviderRegistration.noop();
        }
        String id = normalize(provider.id());
        if (id.isBlank()) {
            return ContributionProviderRegistration.noop();
        }
        attributeService.registerContributionProvider(provider);
        long generation = ++registrationGeneration;
        registrations.put(id, new RegisteredProvider(owner, provider, generation));
        return new RegistrationHandle(this, id, provider, generation);
    }

    public synchronized void unregisterOwner(Plugin owner) {
        if (owner == null || attributeService == null) {
            return;
        }
        Iterator<Map.Entry<String, RegisteredProvider>> iterator = registrations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, RegisteredProvider> entry = iterator.next();
            RegisteredProvider registered = entry.getValue();
            if (registered.owner() != owner) {
                continue;
            }
            iterator.remove();
            attributeService.unregisterContributionProvider(entry.getKey(), registered.provider());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        unregisterOwner(event.getPlugin());
    }

    @Override
    public synchronized void close() {
        HandlerList.unregisterAll(this);
        if (attributeService != null) {
            for (Map.Entry<String, RegisteredProvider> entry : registrations.entrySet()) {
                attributeService.unregisterContributionProvider(entry.getKey(), entry.getValue().provider());
            }
        }
        registrations.clear();
    }

    private synchronized void unregister(String id,
            AttributeContributionProvider provider,
            long generation) {
        RegisteredProvider registered = registrations.get(id);
        if (registered == null || registered.generation() != generation || registered.provider() != provider) {
            return;
        }
        registrations.remove(id);
        attributeService.unregisterContributionProvider(id, provider);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record RegisteredProvider(Plugin owner,
            AttributeContributionProvider provider,
            long generation) {
    }

    private static final class RegistrationHandle implements ContributionProviderRegistration {

        private final ContributionProviderRegistrationRegistry registry;
        private final String id;
        private final AttributeContributionProvider provider;
        private final long generation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RegistrationHandle(ContributionProviderRegistrationRegistry registry,
                String id,
                AttributeContributionProvider provider,
                long generation) {
            this.registry = registry;
            this.id = id;
            this.provider = provider;
            this.generation = generation;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                registry.unregister(id, provider, generation);
            }
        }
    }
}
