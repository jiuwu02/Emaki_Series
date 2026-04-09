package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        } catch (Throwable throwable) {
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
            case READY ->
                messageService.info("console.item_source_bridge_ready", Map.of(
                        "library", resolver.pluginName()
                ));
            case WAITING ->
                messageService.info("console.item_source_bridge_waiting", Map.of(
                        "library", resolver.pluginName(),
                        "detail", defaultWaitingDetail(resolver.pluginName(), status.detail())
                ));
            case INCOMPATIBLE ->
                messageService.warning("console.item_source_bridge_incompatible", Map.of(
                        "library", resolver.pluginName(),
                        "detail", defaultIncompatibleDetail(resolver.pluginName(), status.detail())
                ));
        }
    }

    public Map<String, ManagedItemSourceResolver.Status> statuses() {
        return Map.copyOf(new LinkedHashMap<>(lastStatuses));
    }

    public int detectedResolverCount() {
        return lastStatuses.size();
    }

    public int readyResolverCount() {
        return (int) lastStatuses.values().stream()
                .filter(status -> status != null && status.state() == ManagedItemSourceResolver.State.READY)
                .count();
    }

    public int managedResolverCount() {
        return managedResolvers.size();
    }

    private String defaultWaitingDetail(String library, String detail) {
        return defaultDetail(detail, switch (Texts.lower(library)) {
            case "itemsadder" ->
                "插件已启用，但物品注册尚未完成，请等待其加载流程结束。";
            case "craftengine" ->
                "插件已启用，但物品表尚未完成重载，请等待其重载事件结束。";
            case "neigeitems" ->
                "插件已启用，但物品库尚未完成刷新，请等待其重载完成。";
            case "nexo" ->
                "插件已启用，但物品表尚未完成初始化，请等待其加载事件结束。";
            default ->
                "外部物品注册尚未完成，请等待依赖插件完成加载。";
        });
    }

    private String defaultIncompatibleDetail(String library, String detail) {
        return defaultDetail(detail, library + " 已检测到，但当前 API 结构与 CoreLib 适配器不兼容。");
    }

    private String defaultDetail(String detail, String fallback) {
        return Texts.isBlank(detail) ? fallback : detail;
    }

    private boolean equalsPluginName(String left, String right) {
        return Texts.lower(left).equals(Texts.lower(right));
    }
}
