package emaki.jiuwu.craft.gem.integration;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

/**
 * EmakiAttribute-neutral view of the attribute operations EmakiGem needs.
 *
 * <p>No signature here references an EmakiAttributeApi type, so EmakiGem loads
 * and runs with EmakiAttribute absent. The implementation that does reference
 * those types lives in {@code emaki.jiuwu.craft.gem.integration.attribute} and
 * is only class-loaded once EmakiAttribute is enabled.
 */
public interface GemAttributeBridge {

    /** Bridge used when EmakiAttribute is not installed; every call is a no-op. */
    GemAttributeBridge UNAVAILABLE = new GemAttributeBridge() {
    };

    /** {@return whether EmakiAttribute is available for attribute operations} */
    default boolean available() {
        return false;
    }

    /**
     * Registers an attribute PDC source id, replacing any previous registration.
     *
     * @param sourceId the source id to register
     */
    default void syncRegistration(String sourceId) {
    }

    /** Releases the source id currently registered through this bridge. */
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
     * Copies every stored payload in full, overwriting the destination per
     * source id and skipping the excluded sources.
     *
     * @param fromItem the item to read payloads from
     * @param toItem the item to write payloads to
     * @param excludedSourceIds source ids to skip
     */
    default void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
    }
}
