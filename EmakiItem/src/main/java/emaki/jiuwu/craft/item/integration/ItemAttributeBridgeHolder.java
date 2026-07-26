package emaki.jiuwu.craft.item.integration;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Lifecycle-aware {@link ItemAttributeBridge} that conditionally loads
 * EmakiItem's optional EmakiAttribute integration.
 *
 * <p>The EmakiAttributeApi-referencing implementation is resolved reflectively,
 * so its class only loads when EmakiAttribute is enabled; EmakiItem starts
 * normally otherwise. The delegate is dropped when EmakiAttribute is disabled
 * and rebuilt (re-registering the source) when it returns, preserving the
 * previous gateway's late-provider behaviour without retaining a stale bridge.
 */
public final class ItemAttributeBridgeHolder implements ItemAttributeBridge {

    private static final String ATTRIBUTE_PLUGIN_NAME = "EmakiAttribute";
    private static final String BRIDGE_CLASS =
            "emaki.jiuwu.craft.item.integration.attribute.EmakiAttributeItemBridge";

    private final Logger logger;
    private volatile ItemAttributeBridge delegate = ItemAttributeBridge.UNAVAILABLE;
    private volatile String registeredSourceId;

    /**
     * Creates the holder.
     *
     * @param logger logger used to report integration wiring failures
     */
    public ItemAttributeBridgeHolder(Logger logger) {
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
        ItemAttributeBridge current = delegate;
        delegate = ItemAttributeBridge.UNAVAILABLE;
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
    public Map<String, Double> readAttributes(ItemStack itemStack, String sourceId) {
        return resolve().readAttributes(itemStack, sourceId);
    }

    @Override
    public Map<String, String> readMeta(ItemStack itemStack, String sourceId) {
        return resolve().readMeta(itemStack, sourceId);
    }

    @Override
    public boolean hasPayload(ItemStack itemStack, String sourceId) {
        return resolve().hasPayload(itemStack, sourceId);
    }

    @Override
    public void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
        resolve().copyPayloads(fromItem, toItem, excludedSourceIds);
    }

    @Override
    public void scheduleEquipmentSync(Player player) {
        resolve().scheduleEquipmentSync(player);
    }

    private ItemAttributeBridge resolve() {
        boolean enabled = Bukkit.getPluginManager().isPluginEnabled(ATTRIBUTE_PLUGIN_NAME);
        ItemAttributeBridge current = delegate;
        if (!enabled) {
            if (current != ItemAttributeBridge.UNAVAILABLE) {
                delegate = ItemAttributeBridge.UNAVAILABLE;
            }
            return ItemAttributeBridge.UNAVAILABLE;
        }
        if (current != ItemAttributeBridge.UNAVAILABLE) {
            return current;
        }
        ItemAttributeBridge resolved = load();
        delegate = resolved;
        String sourceId = registeredSourceId;
        if (sourceId != null) {
            resolved.syncRegistration(sourceId);
        }
        return resolved;
    }

    private ItemAttributeBridge load() {
        try {
            Class<?> bridgeClass = Class.forName(BRIDGE_CLASS, true, getClass().getClassLoader());
            Object bridge = bridgeClass.getMethod("create").invoke(null);
            return bridge instanceof ItemAttributeBridge resolved ? resolved : ItemAttributeBridge.UNAVAILABLE;
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (logger != null) {
                logger.warning("Failed to initialize EmakiAttribute integration: " + detail(exception));
            }
            return ItemAttributeBridge.UNAVAILABLE;
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
