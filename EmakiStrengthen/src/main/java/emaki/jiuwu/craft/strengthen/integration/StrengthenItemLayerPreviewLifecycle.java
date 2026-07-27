package emaki.jiuwu.craft.strengthen.integration;

import java.lang.reflect.InvocationTargetException;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;

public final class StrengthenItemLayerPreviewLifecycle implements Listener, AutoCloseable {

    private static final String ITEM_PLUGIN_NAME = "EmakiItem";
    private static final String PROVIDER_CLASS =
            "emaki.jiuwu.craft.strengthen.integration.item.StrengthenItemLayerPreviewProvider";

    private final EmakiStrengthenPlugin plugin;
    private AutoCloseable registration;
    private boolean initialized;

    public StrengthenItemLayerPreviewLifecycle(EmakiStrengthenPlugin plugin) {
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
        if (isItemPlugin(event.getPlugin())) {
            registerIfAvailable();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (isItemPlugin(event.getPlugin())) {
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
                || !plugin.getServer().getPluginManager().isPluginEnabled(ITEM_PLUGIN_NAME)) {
            return;
        }
        try {
            Class<?> providerClass = Class.forName(PROVIDER_CLASS, true, plugin.getClass().getClassLoader());
            Object handle = providerClass.getMethod("register", EmakiStrengthenPlugin.class).invoke(null, plugin);
            if (handle instanceof AutoCloseable closeable) {
                registration = closeable;
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Failed to register EmakiItem strengthen preview integration: "
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
            plugin.getLogger().warning("Failed to release EmakiItem strengthen preview integration: "
                    + detail(exception));
        }
    }

    private boolean isItemPlugin(Plugin candidate) {
        return candidate != null && ITEM_PLUGIN_NAME.equalsIgnoreCase(candidate.getName());
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
