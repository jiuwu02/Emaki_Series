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
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProvider;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRegistration;
import emaki.jiuwu.craft.corelib.api.itemsource.LifecycleState;
import emaki.jiuwu.craft.corelib.api.itemsource.LifecycleStatus;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ItemSourceIntegrationCoordinator implements Listener, AutoCloseable {

    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final ItemSourceService itemSourceService;
    private final List<ManagedItemSourceProvider> managedProviders;
    private final Map<String, LifecycleStatus> lastStatuses = new HashMap<>();
    private final Map<String, ItemSourceRegistration> registrations = new HashMap<>();
    private final Set<String> loadEventBindings = new HashSet<>();
    private boolean initialized;

    public ItemSourceIntegrationCoordinator(
            JavaPlugin plugin,
            MessageService messageService,
            ItemSourceService itemSourceService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.itemSourceService = Objects.requireNonNull(itemSourceService, "itemSourceService");
        this.managedProviders = new ArrayList<>();
    }

    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registerProvidersForEnabledPlugins();
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
        if (registerProviderForPlugin(enabledPlugin.getName())) {
            return;
        }
        for (ManagedItemSourceProvider provider : managedProviders) {
            if (!equalsPluginName(provider.providerPluginName(), enabledPlugin.getName())) {
                continue;
            }
            ensureLoadEventListener(provider);
            // The plugin merely enabled; whether its items are loaded is still for the provider to detect.
            publishStatus(provider, provider.onProviderReady(false));
        }
    }

    private void handlePluginDisable(Plugin disabledPlugin) {
        if (disabledPlugin == null) {
            return;
        }
        for (ManagedItemSourceProvider provider : managedProviders) {
            if (!equalsPluginName(provider.providerPluginName(), disabledPlugin.getName())) {
                continue;
            }
            provider.onProviderDisabled();
            lastStatuses.remove(provider.kind().key());
        }
    }

    private void ensureLoadEventListener(ManagedItemSourceProvider provider) {
        if (provider == null || !loadEventBindings.add(provider.kind().key())) {
            return;
        }
        // The load event is authoritative, so it passes itemsLoaded=true rather than re-detecting.
        provider.registerLoadEventListener(plugin,
                loaded -> publishStatus(loaded, loaded.onProviderReady(true)));
    }

    private void registerProvidersForEnabledPlugins() {
        registerProviderForPlugin("NeigeItems");
        registerProviderForPlugin("CraftEngine");
        registerProviderForPlugin("MMOItems");
        registerProviderForPlugin("ItemsAdder");
        registerProviderForPlugin("Nexo");
        registerProviderForPlugin("Oraxen");
        registerProviderForPlugin("EcoItems");
    }

    private boolean registerProviderForPlugin(String pluginName) {
        ItemSourceProvider provider = createProvider(pluginName);
        return provider != null && registerProvider(provider);
    }

    private boolean registerProvider(ItemSourceProvider provider) {
        if (provider == null) {
            return false;
        }
        String kindKey = provider.kind().key();
        if (registrations.containsKey(kindKey)) {
            return false;
        }
        // Owner is CoreLib's own plugin instance: these bridges are revoked when CoreLib shuts down,
        // not when the bridged plugin disables — a disabled plugin only moves the provider to ABSENT.
        ItemSourceRegistration registration = itemSourceService.registerProvider(plugin, provider);
        if (!registration.successful()) {
            return false;
        }
        registrations.put(kindKey, registration);
        if (provider instanceof ManagedItemSourceProvider managedProvider) {
            managedProviders.add(managedProvider);
            ensureLoadEventListener(managedProvider);
            publishStatus(managedProvider, managedProvider.bootstrap());
        }
        return true;
    }

    private ItemSourceProvider createProvider(String pluginName) {
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

    private void publishStatus(ManagedItemSourceProvider provider, LifecycleStatus status) {
        if (provider == null || status == null) {
            return;
        }
        String kindKey = provider.kind().key();
        LifecycleStatus previous = lastStatuses.get(kindKey);
        if (Objects.equals(previous, status)) {
            return;
        }
        lastStatuses.put(kindKey, status);
        switch (status.state()) {
            case ABSENT -> {
                return;
            }
            case READY ->
                messageService.info("console.item_source_bridge_ready", Map.of(
                        "library", provider.providerPluginName()
                ));
            case WAITING ->
                messageService.info("console.item_source_bridge_waiting", Map.of(
                        "library", provider.providerPluginName(),
                        "detail", defaultWaitingDetail(provider.providerPluginName(), status.detail())
                ));
            case INCOMPATIBLE ->
                messageService.warning("console.item_source_bridge_incompatible", Map.of(
                        "library", provider.providerPluginName(),
                        "detail", defaultIncompatibleDetail(provider.providerPluginName(), status.detail())
                ));
        }
    }

    /** {@return the last published status per provider kind} */
    public Map<String, LifecycleStatus> statuses() {
        return Map.copyOf(new LinkedHashMap<>(lastStatuses));
    }

    public int detectedResolverCount() {
        return lastStatuses.size();
    }

    public int readyResolverCount() {
        return (int) lastStatuses.values().stream()
                .filter(status -> status != null && status.state() == LifecycleState.READY)
                .count();
    }

    public int managedResolverCount() {
        return managedProviders.size();
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
            HandlerList.unregisterAll(this);
        }
        for (ItemSourceRegistration registration : List.copyOf(registrations.values())) {
            registration.close();
        }
        registrations.clear();
        managedProviders.clear();
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
