package emaki.jiuwu.craft.forge.integration;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * Lifecycle-aware {@link ForgeAttributeBridge} that conditionally loads
 * EmakiForge's optional EmakiAttribute integration.
 *
 * <p>The EmakiAttributeApi-referencing implementation is resolved reflectively,
 * so its class only loads when EmakiAttribute is enabled; EmakiForge starts
 * normally otherwise. The delegate is dropped when EmakiAttribute is disabled
 * and rebuilt (re-registering the source) when it returns, preserving the
 * previous gateway's late-provider behaviour without retaining a stale bridge.
 */
public final class ForgeAttributeBridgeHolder implements ForgeAttributeBridge {

    private static final String ATTRIBUTE_PLUGIN_NAME = "EmakiAttribute";
    private static final String BRIDGE_CLASS =
            "emaki.jiuwu.craft.forge.integration.attribute.EmakiAttributeForgeBridge";

    private final Logger logger;
    private volatile ForgeAttributeBridge delegate = ForgeAttributeBridge.UNAVAILABLE;
    private volatile String registeredSourceId;

    /**
     * Creates the holder.
     *
     * @param logger logger used to report integration wiring failures
     */
    public ForgeAttributeBridgeHolder(Logger logger) {
        this.logger = logger;
    }

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
        ForgeAttributeBridge current = delegate;
        delegate = ForgeAttributeBridge.UNAVAILABLE;
        registeredSourceId = null;
        current.shutdown();
    }

    @Override
    public boolean write(ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        return resolve().write(itemStack, sourceId, attributes, meta);
    }

    @Override
    public boolean clear(ItemStack itemStack, String sourceId) {
        return resolve().clear(itemStack, sourceId);
    }

    private ForgeAttributeBridge resolve() {
        boolean enabled = Bukkit.getPluginManager().isPluginEnabled(ATTRIBUTE_PLUGIN_NAME);
        ForgeAttributeBridge current = delegate;
        if (!enabled) {
            if (current != ForgeAttributeBridge.UNAVAILABLE) {
                delegate = ForgeAttributeBridge.UNAVAILABLE;
            }
            return ForgeAttributeBridge.UNAVAILABLE;
        }
        if (current != ForgeAttributeBridge.UNAVAILABLE) {
            return current;
        }
        ForgeAttributeBridge resolved = load();
        delegate = resolved;
        String sourceId = registeredSourceId;
        if (sourceId != null) {
            resolved.syncRegistration(sourceId);
        }
        return resolved;
    }

    private ForgeAttributeBridge load() {
        try {
            Class<?> bridgeClass = Class.forName(BRIDGE_CLASS, true, getClass().getClassLoader());
            Object bridge = bridgeClass.getMethod("create").invoke(null);
            return bridge instanceof ForgeAttributeBridge resolved ? resolved : ForgeAttributeBridge.UNAVAILABLE;
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (logger != null) {
                logger.warning("Failed to initialize EmakiAttribute integration: " + detail(exception));
            }
            return ForgeAttributeBridge.UNAVAILABLE;
        }
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
