package emaki.jiuwu.craft.item.integration;

import java.lang.reflect.InvocationTargetException;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.item.EmakiItemPlugin;

/**
 * Registers EmakiItem's condition gate with EmakiAttribute when available.
 *
 * <p>The gate lets an item definition's {@code condition} block veto that item's
 * whole attribute and skill contribution. The provider class is resolved
 * reflectively so EmakiItem starts normally when EmakiAttribute is absent, and
 * the registration follows EmakiAttribute's enable/disable cycle.
 */
public final class ItemContributionGateLifecycle implements Listener, AutoCloseable {

    private static final String ATTRIBUTE_PLUGIN_NAME = "EmakiAttribute";
    private static final String GATE_CLASS =
            "emaki.jiuwu.craft.item.integration.attribute.ItemConditionContributionGate";

    private final EmakiItemPlugin plugin;
    private AutoCloseable registration;
    private boolean initialized;

    public ItemContributionGateLifecycle(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registerIfAvailable();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (isAttributePlugin(event.getPlugin())) {
            registerIfAvailable();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (isAttributePlugin(event.getPlugin())) {
            closeRegistration();
        }
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        closeRegistration();
        initialized = false;
    }

    private void registerIfAvailable() {
        if (registration != null
                || !plugin.getServer().getPluginManager().isPluginEnabled(ATTRIBUTE_PLUGIN_NAME)) {
            return;
        }
        try {
            Class<?> gateClass = Class.forName(GATE_CLASS, true, plugin.getClass().getClassLoader());
            Object handle = gateClass.getMethod("register", EmakiItemPlugin.class).invoke(null, plugin);
            if (handle instanceof AutoCloseable closeable) {
                registration = closeable;
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Failed to register EmakiAttribute item condition gate: "
                    + detail(exception));
        }
    }

    private void closeRegistration() {
        AutoCloseable current = registration;
        registration = null;
        if (current == null) {
            return;
        }
        try {
            current.close();
        } catch (Exception | LinkageError exception) {
            plugin.getLogger().warning("Failed to release EmakiAttribute item condition gate: "
                    + detail(exception));
        }
    }

    private boolean isAttributePlugin(Plugin candidate) {
        return candidate != null && ATTRIBUTE_PLUGIN_NAME.equalsIgnoreCase(candidate.getName());
    }

    private String detail(Throwable throwable) {
        Throwable resolved = throwable instanceof InvocationTargetException invocation
                && invocation.getCause() != null
                ? invocation.getCause()
                : throwable;
        String message = resolved.getMessage();
        return message == null || message.isBlank() ? resolved.getClass().getSimpleName() : message;
    }
}
