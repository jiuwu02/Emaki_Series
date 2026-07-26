package emaki.jiuwu.craft.item.integration;

import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * EmakiAttribute-neutral view of the attribute operations EmakiItem needs.
 *
 * <p>No method signature on this interface references an EmakiAttributeApi type,
 * so EmakiItem can load and run with EmakiAttribute absent. The implementation
 * that does reference those types lives in
 * {@code emaki.jiuwu.craft.item.integration.attribute} and is only class-loaded
 * once EmakiAttribute is enabled.
 */
public interface ItemAttributeBridge {

    /** Bridge used when EmakiAttribute is not installed; every call is a no-op. */
    ItemAttributeBridge UNAVAILABLE = new ItemAttributeBridge() {
    };

    /** {@return whether EmakiAttribute is available for attribute operations} */
    default boolean available() {
        return false;
    }

    /**
     * Registers an attribute PDC source id, replacing any previous registration
     * owned by this module.
     *
     * @param sourceId the source id to register
     */
    default void syncRegistration(String sourceId) {
    }

    /**
     * Releases the source id currently registered through this bridge.
     */
    default void shutdown() {
    }

    /**
     * Writes an attribute payload for a source, preserving all payload fields.
     *
     * @param itemStack the target item
     * @param sourceId the owning source id
     * @param attributes attribute id to value mapping
     * @param meta string metadata
     * @return {@code true} when the item was modified
     */
    default boolean write(ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        return false;
    }

    /**
     * Removes the payload of a single source.
     *
     * @param itemStack the target item
     * @param sourceId the source id to clear
     * @return {@code true} when data was removed
     */
    default boolean clear(ItemStack itemStack, String sourceId) {
        return false;
    }

    /**
     * Reads the attribute values stored for a source.
     *
     * @param itemStack the item to read
     * @param sourceId the source id
     * @return attribute id to value mapping; empty when absent or unavailable
     */
    default Map<String, Double> readAttributes(ItemStack itemStack, String sourceId) {
        return Map.of();
    }

    /**
     * Reads the string metadata stored for a source.
     *
     * @param itemStack the item to read
     * @param sourceId the source id
     * @return metadata mapping; empty when absent or unavailable
     */
    default Map<String, String> readMeta(ItemStack itemStack, String sourceId) {
        return Map.of();
    }

    /**
     * {@return whether the item stores a payload for the source}
     *
     * @param itemStack the item to read
     * @param sourceId the source id
     */
    default boolean hasPayload(ItemStack itemStack, String sourceId) {
        return false;
    }

    /**
     * Copies every stored payload in full, overwriting the destination per
     * source id and skipping the excluded sources.
     *
     * @param fromItem the item to read payloads from
     * @param toItem the item to write payloads to
     * @param excludedSourceIds source ids to skip
     */
    default void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
    }

    /**
     * Requests an equipment attribute resynchronization for a player.
     *
     * @param player the player to resynchronize
     */
    default void scheduleEquipmentSync(Player player) {
    }
}
