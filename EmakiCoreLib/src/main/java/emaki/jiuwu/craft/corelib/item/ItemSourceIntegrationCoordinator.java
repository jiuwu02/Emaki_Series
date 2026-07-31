package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ItemSourceIntegrationCoordinator implements Listener, AutoCloseable {

    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final ItemSourceService itemSourceService;
    private final List<ManagedItemSourceResolver> managedResolvers;
    private final Map<String, ManagedItemSourceResolver.Status> lastStatuses = new HashMap<>();
    private final Map<String, ItemSourceService.ResolverRegistration> resolverRegistrations = new HashMap<>();
    private final Set<String> loadEventBindings = new HashSet<>();
    private boolean initialized;

    public ItemSourceIntegrationCoordinator(
            JavaPlugin plugin,
            MessageService messageService,
            ItemSourceService itemSourceService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.itemSourceService = Objects.requireNonNull(itemSourceService, "itemSourceService");
        this.managedResolvers = new ArrayList<>();
    }

    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registerResolversForEnabledPlugins();
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
        if (registerResolverForPlugin(enabledPlugin.getName())) {
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
        if (resolver == null || !loadEventBindings.add(Texts.normalizeId(resolver.id()))) {
            return;
        }
        resolver.registerLoadEventListener(plugin, loadedResolver -> publishStatus(loadedResolver, loadedResolver.onItemsLoaded()));
    }

    private void registerResolversForEnabledPlugins() {
        registerResolverForPlugin("NeigeItems");
        registerResolverForPlugin("CraftEngine");
        registerResolverForPlugin("MMOItems");
        registerResolverForPlugin("ItemsAdder");
        registerResolverForPlugin("Nexo");
        registerResolverForPlugin("Oraxen");
        registerResolverForPlugin("EcoItems");
    }

    private boolean registerResolverForPlugin(String pluginName) {
        ItemSourceResolver resolver = createResolver(pluginName);
        if (resolver == null) {
            return false;
        }
        return registerResolver(resolver);
    }

    private boolean registerResolver(ItemSourceResolver resolver) {
        if (resolver == null || Texts.isBlank(resolver.id())) {
            return false;
        }
        String resolverId = Texts.normalizeId(resolver.id());
        if (resolverRegistrations.containsKey(resolverId)) {
            return false;
        }
        ItemSourceService.ResolverRegistration registration = itemSourceService.registerResolverHandle(resolver);
        if (!registration.registered()) {
            return false;
        }
        resolverRegistrations.put(resolverId, registration);
        if (resolver instanceof ManagedItemSourceResolver managedResolver) {
            managedResolvers.add(managedResolver);
            ensureLoadEventListener(managedResolver);
            publishStatus(managedResolver, managedResolver.bootstrap());
        }
        return true;
    }

    private ItemSourceResolver createResolver(String pluginName) {
        if (!isPluginEnabled(pluginName)) {
            return null;
        }
        try {
            return switch (Texts.lower(pluginName)) {
                case "neigeitems" -> new NeigeItemsItemSourceResolver();
                case "craftengine" -> new CraftEngineItemSourceResolver();
                case "mmoitems" -> new MmoItemsItemSourceResolver();
                case "itemsadder" -> new ItemsAdderItemSourceResolver();
                case "nexo" -> new NexoItemSourceResolver();
                case "oraxen" -> new OraxenItemSourceResolver();
                case "ecoitems" -> new EcoItemsItemSourceResolver();
                default -> null;
            };
        } catch (LinkageError exception) {
            messageService.warning("console.item_source_bridge_incompatible", Map.of(
                    "library", pluginName,
                    "detail", defaultIncompatibleDetail(pluginName, exception.getMessage())
            ));
            return null;
        }
    }

    private boolean isPluginEnabled(String pluginName) {
        return Texts.isNotBlank(pluginName) && plugin.getServer().getPluginManager().isPluginEnabled(pluginName);
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

    @Override
    public void close() {
        closeInternal(true);
    }

    public void closeAfterBukkitUnregister() {
        closeInternal(false);
    }

    private void closeInternal(boolean unregisterBukkitListeners) {
        if (unregisterBukkitListeners) {
            org.bukkit.event.HandlerList.unregisterAll(this);
        }
        for (ItemSourceService.ResolverRegistration registration : List.copyOf(resolverRegistrations.values())) {
            registration.close();
        }
        resolverRegistrations.clear();
        managedResolvers.clear();
        lastStatuses.clear();
        loadEventBindings.clear();
        initialized = false;
    }

    private String defaultWaitingDetail(String library, String detail) {
        if (Texts.isNotBlank(detail)) {
            return detail;
        }
        // %detail% 会填进双语模板 console.item_source_bridge_waiting，因此填充值本身也必须走 lang，
        // 否则英文服会得到英文模板 + 中文详情的混排输出。
        String specific = localizedDetail("console.item_source.waiting_detail." + Texts.lower(library));
        return Texts.isNotBlank(specific)
                ? specific
                : localizedDetail("console.item_source.waiting_detail.default");
    }

    private String defaultIncompatibleDetail(String library, String detail) {
        if (Texts.isNotBlank(detail)) {
            return detail;
        }
        return localizedDetail("console.item_source.incompatible_detail", Map.of("library", Texts.toStringSafe(library)));
    }

    private String localizedDetail(String key) {
        return localizedDetail(key, Map.of());
    }

    private String localizedDetail(String key, Map<String, ?> replacements) {
        String resolved = messageService.message(key, replacements);
        // MessageService 在缺键时回显 key 本身，这里不把 key 当作可展示文案输出。
        return Texts.isBlank(resolved) || key.equals(resolved) ? "" : resolved;
    }

    private boolean equalsPluginName(String left, String right) {
        return Texts.lower(left).equals(Texts.lower(right));
    }
}
