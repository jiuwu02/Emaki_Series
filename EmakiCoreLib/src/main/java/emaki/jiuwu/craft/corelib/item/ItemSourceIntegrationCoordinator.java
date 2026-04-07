package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ItemSourceIntegrationCoordinator implements Listener {

    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final ItemSourceService itemSourceService;
    private final List<ItemSourceResolver> resolvers;
    private final List<ManagedItemSourceResolver> managedResolvers;
    private final Map<String, ManagedItemSourceResolver.Status> lastStatuses = new HashMap<>();
    private final Map<String, ManagedItemSourceResolver> loadEventBindings = new HashMap<>();
    private boolean initialized;

    public ItemSourceIntegrationCoordinator(
            JavaPlugin plugin,
            MessageService messageService,
            ItemSourceService itemSourceService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.itemSourceService = Objects.requireNonNull(itemSourceService, "itemSourceService");
        this.resolvers = List.of(
                new ReflectiveNeigeItemsItemSourceResolver(),
                new ReflectiveCraftEngineItemSourceResolver(),
                new ReflectiveMmoItemsItemSourceResolver(),
                new ReflectiveItemsAdderItemSourceResolver(),
                new ReflectiveNexoItemSourceResolver()
        );
        List<ManagedItemSourceResolver> managed = new ArrayList<>();
        for (ItemSourceResolver resolver : resolvers) {
            if (resolver instanceof ManagedItemSourceResolver managedResolver) {
                managed.add(managedResolver);
            }
        }
        this.managedResolvers = List.copyOf(managed);
    }

    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        for (ItemSourceResolver resolver : resolvers) {
            itemSourceService.registerResolver(resolver);
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (ManagedItemSourceResolver resolver : managedResolvers) {
            ensureLoadEventListener(resolver);
            publishStatus(resolver, resolver.bootstrap());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        handlePluginEnable(event.getPlugin());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        handlePluginDisable(event.getPlugin());
    }

    private void handlePluginEnable(Plugin enabledPlugin) {
        if (enabledPlugin == null) {
            return;
        }
        for (ManagedItemSourceResolver resolver : managedResolvers) {
            if (!equalsPluginName(resolver.pluginName(), enabledPlugin.getName())) {
                continue;
            }
            ensureLoadEventListener(resolver);
            publishStatus(resolver, resolver.onPluginEnabled());
        }
    }

    private void handlePluginDisable(Plugin disabledPlugin) {
        if (disabledPlugin == null) {
            return;
        }
        for (ManagedItemSourceResolver resolver : managedResolvers) {
            if (!equalsPluginName(resolver.pluginName(), disabledPlugin.getName())) {
                continue;
            }
            resolver.onPluginDisabled();
            lastStatuses.remove(resolver.id());
        }
    }

    private void ensureLoadEventListener(ManagedItemSourceResolver resolver) {
        String eventClassName = resolver.loadEventClassName();
        if (Texts.isBlank(eventClassName) || loadEventBindings.containsKey(eventClassName)) {
            return;
        }
        try {
            Class<?> rawEventClass = Class.forName(eventClassName);
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            plugin.getServer().getPluginManager().registerEvent(
                    eventClass,
                    this,
                    EventPriority.MONITOR,
                    (listener, event) -> publishStatus(resolver, resolver.onItemsLoaded()),
                    plugin,
                    true
            );
            loadEventBindings.put(eventClassName, resolver);
        } catch (Throwable ignored) {
            // Defer registration until the dependency is actually present.
        }
    }

    private void publishStatus(ManagedItemSourceResolver resolver, ManagedItemSourceResolver.Status status) {
        if (resolver == null || status == null) {
            return;
        }
        ManagedItemSourceResolver.Status previous = lastStatuses.get(resolver.id());
        if (Objects.equals(previous, status)) {
            return;
        }
        lastStatuses.put(resolver.id(), status);
        switch (status.state()) {
            case ABSENT -> {
                return;
            }
            case READY -> messageService.info("console.item_source_bridge_ready", Map.of(
                    "library", resolver.pluginName()
            ));
            case WAITING -> messageService.info("console.item_source_bridge_waiting", Map.of(
                    "library", resolver.pluginName(),
                    "detail", defaultDetail(status.detail(), "Waiting for the external item registry to finish loading.")
            ));
            case INCOMPATIBLE -> messageService.warning("console.item_source_bridge_incompatible", Map.of(
                    "library", resolver.pluginName(),
                    "detail", defaultDetail(status.detail(), "The external API could not be resolved.")
            ));
        }
    }

    private String defaultDetail(String detail, String fallback) {
        return Texts.isBlank(detail) ? fallback : detail;
    }

    private boolean equalsPluginName(String left, String right) {
        return Texts.lower(left).equals(Texts.lower(right));
    }
}
