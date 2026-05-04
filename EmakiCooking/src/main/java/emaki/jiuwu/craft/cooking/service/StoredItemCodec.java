package emaki.jiuwu.craft.cooking.service;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import org.bukkit.inventory.ItemStack;

final class StoredItemCodec {

    private StoredItemCodec() {
    }

    static Map<String, Object> serialize(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return Map.of();
        }
        Object plain = ConfigNodes.toPlainData(itemStack.serialize());
        if (!(plain instanceof Map<?, ?> itemMap)) {
            return Map.of();
        }
        return Map.copyOf(MapYamlSection.normalizeMap(itemMap));
    }

    static ItemStack deserialize(Map<String, Object> serializedItem) {
        if (serializedItem == null || serializedItem.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserialize(new LinkedHashMap<>(serializedItem));
        } catch (Exception _) {
            return null;
        }
    }
}
