package emaki.jiuwu.craft.gem.integration;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

public final class GemAttributeBridgeHolder implements GemAttributeBridge {

    private static final String ATTRIBUTE_PLUGIN_NAME = "EmakiAttribute";
    private static final String BRIDGE_CLASS =
            "emaki.jiuwu.craft.gem.integration.attribute.EmakiAttributeGemBridge";

    private final Logger logger;
    private volatile GemAttributeBridge delegate = GemAttributeBridge.UNAVAILABLE;
    private volatile String registeredSourceId;

    public GemAttributeBridgeHolder(Logger logger) {
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
        GemAttributeBridge current = delegate;
        delegate = GemAttributeBridge.UNAVAILABLE;
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

    @Override
    public void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
        resolve().copyPayloads(fromItem, toItem, excludedSourceIds);
    }

    private GemAttributeBridge resolve() {
        boolean enabled = Bukkit.getPluginManager().isPluginEnabled(ATTRIBUTE_PLUGIN_NAME);
        GemAttributeBridge current = delegate;
        if (!enabled) {
            if (current != GemAttributeBridge.UNAVAILABLE) {
                delegate = GemAttributeBridge.UNAVAILABLE;
            }
            return GemAttributeBridge.UNAVAILABLE;
        }
        if (current != GemAttributeBridge.UNAVAILABLE) {
            return current;
        }
        GemAttributeBridge resolved = load();
        delegate = resolved;
        String sourceId = registeredSourceId;
        if (sourceId != null) {
            resolved.syncRegistration(sourceId);
        }
        return resolved;
    }

    private GemAttributeBridge load() {
        try {
            Class<?> bridgeClass = Class.forName(BRIDGE_CLASS, true, getClass().getClassLoader());
            Object bridge = bridgeClass.getMethod("create").invoke(null);
            return bridge instanceof GemAttributeBridge resolved ? resolved : GemAttributeBridge.UNAVAILABLE;
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (logger != null) {
                logger.warning("Failed to initialize EmakiAttribute integration: " + detail(exception));
            }
            return GemAttributeBridge.UNAVAILABLE;
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
