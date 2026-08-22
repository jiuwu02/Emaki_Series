package emaki.jiuwu.craft.gem.integration;

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.integration.AbstractPluginBridgeHolder;

public final class GemAttributeBridgeHolder extends AbstractPluginBridgeHolder<GemAttributeBridge>
        implements GemAttributeBridge {

    private static final String ATTRIBUTE_PLUGIN_NAME = "EmakiAttribute";
    private static final String BRIDGE_CLASS =
            "emaki.jiuwu.craft.gem.integration.attribute.EmakiAttributeGemBridge";

    public GemAttributeBridgeHolder(Logger logger) {
        super(logger, ATTRIBUTE_PLUGIN_NAME, BRIDGE_CLASS, GemAttributeBridge.UNAVAILABLE);
    }

    @Override
    protected GemAttributeBridge cast(Object bridge) {
        return bridge instanceof GemAttributeBridge resolved ? resolved : GemAttributeBridge.UNAVAILABLE;
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
