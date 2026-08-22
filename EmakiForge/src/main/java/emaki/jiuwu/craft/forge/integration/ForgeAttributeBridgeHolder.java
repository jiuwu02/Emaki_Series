package emaki.jiuwu.craft.forge.integration;

import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.integration.AbstractPluginBridgeHolder;

public final class ForgeAttributeBridgeHolder extends AbstractPluginBridgeHolder<ForgeAttributeBridge>
        implements ForgeAttributeBridge {

    private static final String ATTRIBUTE_PLUGIN_NAME = "EmakiAttribute";
    private static final String BRIDGE_CLASS =
            "emaki.jiuwu.craft.forge.integration.attribute.EmakiAttributeForgeBridge";

    public ForgeAttributeBridgeHolder(Logger logger) {
        super(logger, ATTRIBUTE_PLUGIN_NAME, BRIDGE_CLASS, ForgeAttributeBridge.UNAVAILABLE);
    }

    @Override
    protected ForgeAttributeBridge cast(Object bridge) {
        return bridge instanceof ForgeAttributeBridge resolved ? resolved : ForgeAttributeBridge.UNAVAILABLE;
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
}
