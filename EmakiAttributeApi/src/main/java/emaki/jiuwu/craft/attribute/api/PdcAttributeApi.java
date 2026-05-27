package emaki.jiuwu.craft.attribute.api;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.attribute.model.PdcAttributePayload;

/**
 * Public API for reading and writing PDC-backed attribute payloads on items.
 */
public interface PdcAttributeApi {

    /**
     * Registers a payload source id that is allowed to write attribute payloads.
     *
     * @param sourceId the source id to register
     * @return {@code true} when the source was newly registered
     */
    boolean registerSource(@NotNull String sourceId);

    /**
     * Unregisters a payload source id.
     *
     * @param sourceId the source id to unregister
     */
    void unregisterSource(@NotNull String sourceId);

    /**
     * Returns whether a source id is registered.
     *
     * @param sourceId the source id to inspect
     * @return {@code true} when the source id is registered
     */
    boolean isRegisteredSource(@NotNull String sourceId);

    /**
     * Returns all registered source ids.
     *
     * @return the immutable set of registered source ids
     */
    @NotNull
    Set<String> registeredSources();

    /**
     * Writes a payload onto an item.
     *
     * @param itemStack the item to mutate
     * @param payload the payload to write
     * @return {@code true} when the payload was written successfully
     */
    boolean write(@Nullable ItemStack itemStack, @Nullable PdcAttributePayload payload);

    /**
     * Writes a payload using raw source, attribute, and metadata values.
     *
     * @param itemStack the item to mutate
     * @param sourceId the payload source id
     * @param attributes the attribute values to store
     * @param meta the metadata values to store
     * @return {@code true} when the payload was written successfully
     */
    default boolean write(ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        return write(itemStack, PdcAttributePayload.of(sourceId, attributes, meta));
    }

    /**
     * Reads a payload from an item for a specific source id.
     *
     * @param itemStack the item to inspect
     * @param sourceId the payload source id
     * @return the resolved payload, or {@code null} when no payload exists
     */
    @Nullable
    PdcAttributePayload read(@Nullable ItemStack itemStack, @NotNull String sourceId);

    /**
     * Reads all payloads currently stored on an item.
     *
     * @param itemStack the item to inspect
     * @return all payloads keyed by source id
     */
    @NotNull
    Map<String, PdcAttributePayload> readAll(@Nullable ItemStack itemStack);

    /**
     * Clears a payload for a specific source id.
     *
     * @param itemStack the item to mutate
     * @param sourceId the payload source id
     * @return {@code true} when a payload was cleared
     */
    boolean clear(@Nullable ItemStack itemStack, @NotNull String sourceId);

    /**
     * Clears all payloads from an item.
     *
     * @param itemStack the item to mutate
     */
    void clearAll(@Nullable ItemStack itemStack);
}
