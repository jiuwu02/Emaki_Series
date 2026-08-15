package emaki.jiuwu.craft.forge.integration;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

public interface ForgeAttributeBridge {

    ForgeAttributeBridge UNAVAILABLE = new ForgeAttributeBridge() {
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
}
