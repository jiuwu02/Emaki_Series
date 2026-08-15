package emaki.jiuwu.craft.item.integration;

import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface ItemAttributeBridge {

    ItemAttributeBridge UNAVAILABLE = new ItemAttributeBridge() {
    };

    default boolean available() {
        return false;
    }

    default void syncRegistration(String sourceId) {
    }

    default void shutdown() {
    }

    default boolean write(ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        return false;
    }

    default boolean clear(ItemStack itemStack, String sourceId) {
        return false;
    }

    default Map<String, Double> readAttributes(ItemStack itemStack, String sourceId) {
        return Map.of();
    }

    default Map<String, String> readMeta(ItemStack itemStack, String sourceId) {
        return Map.of();
    }

    default boolean hasPayload(ItemStack itemStack, String sourceId) {
        return false;
    }

    default void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
    }

    default void scheduleEquipmentSync(Player player) {
    }
}
