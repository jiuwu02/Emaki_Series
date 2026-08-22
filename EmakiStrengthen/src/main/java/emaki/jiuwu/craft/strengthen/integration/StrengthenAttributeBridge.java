package emaki.jiuwu.craft.strengthen.integration;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.integration.PluginBridge;

public interface StrengthenAttributeBridge extends PluginBridge {

    StrengthenAttributeBridge UNAVAILABLE = new StrengthenAttributeBridge() {
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

    default Map<String, Map<String, Double>> readAllAttributes(ItemStack itemStack) {
        return Map.of();
    }

    default void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
    }
}
