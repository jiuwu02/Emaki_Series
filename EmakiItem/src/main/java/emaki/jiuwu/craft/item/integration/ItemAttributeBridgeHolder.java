package emaki.jiuwu.craft.item.integration;

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.integration.AbstractPluginBridgeHolder;

public final class ItemAttributeBridgeHolder extends AbstractPluginBridgeHolder<ItemAttributeBridge>
        implements ItemAttributeBridge {

    private static final String ATTRIBUTE_PLUGIN_NAME = "EmakiAttribute";
    private static final String BRIDGE_CLASS =
            "emaki.jiuwu.craft.item.integration.attribute.EmakiAttributeItemBridge";

    public ItemAttributeBridgeHolder(Logger logger) {
        super(logger, ATTRIBUTE_PLUGIN_NAME, BRIDGE_CLASS, ItemAttributeBridge.UNAVAILABLE);
    }

    @Override
    protected ItemAttributeBridge cast(Object bridge) {
        return bridge instanceof ItemAttributeBridge resolved ? resolved : ItemAttributeBridge.UNAVAILABLE;
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
}
