package emaki.jiuwu.craft.corelib.integration.furniture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.furniture.FurnitureEvent;
import emaki.jiuwu.craft.corelib.api.furniture.FurnitureEventListener;

public final class FurnitureEventRelay implements Listener {

    private final JavaPlugin owner;
    private final Map<String, Listener> registeredSources = new LinkedHashMap<>();
    private final Set<String> failedSources = new java.util.HashSet<>();
    private final Map<Plugin, FurnitureEventListener> subscribers = new LinkedHashMap<>();

    public FurnitureEventRelay(JavaPlugin owner) {
        this.owner = owner;
    }

    public synchronized void subscribe(Plugin subscriber, FurnitureEventListener listener) {
        if (subscriber == null || listener == null) {
            return;
        }
        subscribers.put(subscriber, listener);
        ensureSourceListeners();
    }

    public synchronized void uninstall(Plugin subscriber) {
        if (subscriber != null) {
            subscribers.remove(subscriber);
        }
    }

    public synchronized void ensureSourceListeners() {
        registerSource("ItemsAdder", () -> new ItemsAdderFurnitureRelayListener(this));
        registerSource("Oraxen", () -> new OraxenFurnitureRelayListener(this));
        registerSource("Nexo", () -> new NexoFurnitureRelayListener(this));
        registerSource("CraftEngine", () -> new CraftEngineFurnitureRelayListener(this));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        String name = event.getPlugin().getName();
        if ("ItemsAdder".equals(name) || "Oraxen".equals(name)
                || "Nexo".equals(name) || "CraftEngine".equals(name)) {
            ensureSourceListeners();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        uninstall(event.getPlugin());
    }

    void dispatch(FurnitureEvent event, Cancellable bukkitEvent) {
        List<FurnitureEventListener> targets;
        synchronized (this) {
            if (subscribers.isEmpty()) {
                return;
            }
            targets = new ArrayList<>(subscribers.values());
        }
        for (FurnitureEventListener target : targets) {
            if (bukkitEvent.isCancelled()) {
                return;
            }
            try {
                target.onFurnitureEvent(event);
            } catch (RuntimeException | LinkageError exception) {
                owner.getLogger().warning("[integration] 家具事件订阅者处理失败: "
                        + exception.getClass().getSimpleName()
                        + (exception.getMessage() == null ? "" : ": " + exception.getMessage()));
            }
        }
    }

    static String normalizedId(String namespace, String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        String normalized = rawId.trim().toLowerCase(Locale.ROOT);
        return normalized.indexOf(':') < 0 ? namespace + ":" + normalized : normalized;
    }

    private void registerSource(String pluginName, Supplier<Listener> factory) {
        if (registeredSources.containsKey(pluginName) || failedSources.contains(pluginName)) {
            return;
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        try {
            Listener listener = factory.get();
            owner.getServer().getPluginManager().registerEvents(listener, owner);
            registeredSources.put(pluginName, listener);
        } catch (LinkageError error) {
            failedSources.add(pluginName);
            owner.getLogger().warning("[integration] 初始化 " + pluginName + " 家具事件监听失败: "
                    + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        }
    }
}
