package emaki.jiuwu.craft.gem.integration;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

public interface GemAttributeBridge {

    GemAttributeBridge UNAVAILABLE = new GemAttributeBridge() {
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

    default void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
    }
}
