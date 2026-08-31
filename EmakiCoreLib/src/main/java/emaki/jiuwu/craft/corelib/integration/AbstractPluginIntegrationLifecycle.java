package emaki.jiuwu.craft.corelib.integration;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class AbstractPluginIntegrationLifecycle<P extends JavaPlugin> implements Listener, AutoCloseable {

    private final P plugin;
    private final Class<P> pluginType;
    private final String dependencyPluginName;
    private final String providerClassName;
    private final String integrationLabel;
    private AutoCloseable registration;
    private boolean initialized;

    protected AbstractPluginIntegrationLifecycle(P plugin,
            Class<P> pluginType,
            String dependencyPluginName,
            String providerClassName,
            String integrationLabel) {
        this.plugin = plugin;
        this.pluginType = pluginType;
        this.dependencyPluginName = dependencyPluginName;
        this.providerClassName = providerClassName;
        this.integrationLabel = integrationLabel;
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
        if (isDependencyPlugin(event.getPlugin())) {
            registerIfAvailable();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (isDependencyPlugin(event.getPlugin())) {
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
                || !plugin.getServer().getPluginManager().isPluginEnabled(dependencyPluginName)) {
            return;
        }
        try {
            Class<?> providerClass = Class.forName(providerClassName, true, plugin.getClass().getClassLoader());
            Object handle = providerClass.getMethod("register", pluginType).invoke(null, plugin);
            if (handle instanceof AutoCloseable closeable) {
                registration = closeable;
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Failed to register " + integrationLabel + ": "
                    + IntegrationFailures.detail(exception));
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
            plugin.getLogger().warning("Failed to release " + integrationLabel + ": "
                    + IntegrationFailures.detail(exception));
        }
    }

    private boolean isDependencyPlugin(Plugin candidate) {
        return candidate != null && dependencyPluginName.equalsIgnoreCase(candidate.getName());
    }
}
