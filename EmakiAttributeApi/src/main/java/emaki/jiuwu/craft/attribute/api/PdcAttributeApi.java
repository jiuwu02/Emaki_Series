package emaki.jiuwu.craft.attribute.api;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.attribute.model.PdcAttributePayload;

/**
 * Public API for reading and writing EmakiAttribute attribute data stored in an
 * item's Persistent Data Container (PDC).
 *
 * <p>Each block of attribute data is keyed by a {@code sourceId}, allowing
 * several plugins to attach independent attribute payloads to the same item
 * without clobbering one another. A source must be registered with
 * {@link #registerSource(String)} before its payloads will be honored.
 *
 * <p>Obtain an instance via {@link PdcAttributeApiProvider#get()}. All item
 * arguments tolerate {@code null} and are treated as "no data".
 */
public interface PdcAttributeApi {

    /**
     * Registers a source id so payloads written under it are recognized.
     *
     * @param sourceId the source identifier to register
     * @return {@code true} if the source was newly registered, {@code false} if
     *         it was already known
     */
    boolean registerSource(@NotNull String sourceId);

    /**
     * Unregisters a previously registered source id.
     *
     * @param sourceId the source identifier to remove
     */
    void unregisterSource(@NotNull String sourceId);

    /**
     * {@return whether the given source id is currently registered}
     *
     * @param sourceId the source identifier to check
     */
    boolean isRegisteredSource(@NotNull String sourceId);

    /**
     * {@return an immutable view of all currently registered source ids}
     */
    @NotNull
    Set<String> registeredSources();

    /**
     * Writes (or replaces) the attribute payload for its source onto the item.
     *
     * @param itemStack the target item; {@code null} is a no-op
     * @param payload   the payload to store; {@code null} is a no-op
     * @return {@code true} if the item was modified
     */
    boolean write(@Nullable ItemStack itemStack, @Nullable PdcAttributePayload payload);

    /**
     * Convenience overload that builds a {@link PdcAttributePayload} from raw
     * maps and writes it.
     *
     * @param itemStack  the target item; {@code null} is a no-op
     * @param sourceId   the source id the payload belongs to
     * @param attributes attribute id to value mapping
     * @param meta       arbitrary string metadata stored alongside the payload
     * @return {@code true} if the item was modified
     */
    default boolean write(ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        return write(itemStack, PdcAttributePayload.of(sourceId, attributes, meta));
    }

    /**
     * Reads the payload stored for a single source.
     *
     * @param itemStack the item to read from; {@code null} yields {@code null}
     * @param sourceId  the source id whose payload to read
     * @return the stored payload, or {@code null} when absent
     */
    @Nullable
    PdcAttributePayload read(@Nullable ItemStack itemStack, @NotNull String sourceId);

    /**
     * Reads every source payload stored on the item.
     *
     * @param itemStack the item to read from; {@code null} yields an empty map
     * @return a map of source id to payload; never {@code null}
     */
    @NotNull
    Map<String, PdcAttributePayload> readAll(@Nullable ItemStack itemStack);

    /**
     * Removes the payload of a single source from the item.
     *
     * @param itemStack the target item; {@code null} is a no-op
     * @param sourceId  the source id to clear
     * @return {@code true} if data was removed
     */
    boolean clear(@Nullable ItemStack itemStack, @NotNull String sourceId);

    /**
     * Removes all EmakiAttribute payloads from the item.
     *
     * @param itemStack the target item; {@code null} is a no-op
     */
    void clearAll(@Nullable ItemStack itemStack);
}
