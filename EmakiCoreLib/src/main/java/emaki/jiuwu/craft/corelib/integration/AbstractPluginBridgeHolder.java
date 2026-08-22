package emaki.jiuwu.craft.corelib.integration;

import java.util.logging.Logger;

import org.bukkit.Bukkit;

public abstract class AbstractPluginBridgeHolder<B extends PluginBridge> implements PluginBridge {

    private final Logger logger;
    private final String dependencyPluginName;
    private final String bridgeClassName;
    private final B unavailable;
    private volatile B delegate;
    private volatile String registeredSourceId;

    protected AbstractPluginBridgeHolder(Logger logger,
            String dependencyPluginName,
            String bridgeClassName,
            B unavailable) {
        this.logger = logger;
        this.dependencyPluginName = dependencyPluginName;
        this.bridgeClassName = bridgeClassName;
        this.unavailable = unavailable;
        this.delegate = unavailable;
    }

    protected abstract B cast(Object bridge);

    @Override
    public boolean available() {
        return resolve().available();
    }

    @Override
    public void syncRegistration(String sourceId) {
        registeredSourceId = sourceId;
        resolve().syncRegistration(sourceId);
    }

    @Override
    public void shutdown() {
        B current = delegate;
        delegate = unavailable;
        registeredSourceId = null;
        current.shutdown();
    }

    protected final B unavailable() {
        return unavailable;
    }

    protected final B resolve() {
        boolean enabled = Bukkit.getPluginManager().isPluginEnabled(dependencyPluginName);
        B current = delegate;
        if (!enabled) {
            if (current != unavailable) {
                delegate = unavailable;
            }
            return unavailable;
        }
        if (current != unavailable) {
            return current;
        }
        B resolved = load();
        delegate = resolved;
        String sourceId = registeredSourceId;
        if (sourceId != null) {
            resolved.syncRegistration(sourceId);
        }
        return resolved;
    }

    private B load() {
        try {
            Class<?> bridgeClass = Class.forName(bridgeClassName, true, getClass().getClassLoader());
            Object bridge = bridgeClass.getMethod("create").invoke(null);
            return cast(bridge);
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (logger != null) {
                logger.warning("Failed to initialize " + dependencyPluginName + " integration: "
                        + IntegrationFailures.detail(exception));
            }
            return unavailable;
        }
    }
}
