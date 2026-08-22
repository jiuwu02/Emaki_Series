package emaki.jiuwu.craft.strengthen.integration;

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.integration.AbstractPluginBridgeHolder;

public final class StrengthenAttributeBridgeHolder extends AbstractPluginBridgeHolder<StrengthenAttributeBridge>
        implements StrengthenAttributeBridge {

    private static final String ATTRIBUTE_PLUGIN_NAME = "EmakiAttribute";
    private static final String BRIDGE_CLASS =
            "emaki.jiuwu.craft.strengthen.integration.attribute.EmakiAttributeStrengthenBridge";

    public StrengthenAttributeBridgeHolder(Logger logger) {
        super(logger, ATTRIBUTE_PLUGIN_NAME, BRIDGE_CLASS, StrengthenAttributeBridge.UNAVAILABLE);
    }

    @Override
    protected StrengthenAttributeBridge cast(Object bridge) {
        return bridge instanceof StrengthenAttributeBridge resolved
                ? resolved
                : StrengthenAttributeBridge.UNAVAILABLE;
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
}
