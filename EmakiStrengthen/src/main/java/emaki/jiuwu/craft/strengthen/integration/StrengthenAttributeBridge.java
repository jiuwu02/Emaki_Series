package emaki.jiuwu.craft.strengthen.integration;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

public interface StrengthenAttributeBridge {

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

    /**
     * 读取物品上全部来源的结构化属性，按 {@code sourceId} 归组。
     *
     * <p>词条强化据此枚举「可强化词条」：词条本质上就是结构化属性中的一条 key。EmakiAttribute
     * 缺失时返回空表，调用方必须把空表理解为「读不到」而非「没有词条」。
     *
     * @param itemStack 待读取物品
     * @return sourceId → (属性 key → 数值)；不可用时为空表
     */
    default Map<String, Map<String, Double>> readAllAttributes(ItemStack itemStack) {
        return Map.of();
    }

    default void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
    }
}
