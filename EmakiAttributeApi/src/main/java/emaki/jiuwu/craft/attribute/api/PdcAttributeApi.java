package emaki.jiuwu.craft.attribute.api;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.attribute.model.PdcAttributePayload;

/**
 * Static public API facade for reading and writing EmakiAttribute attribute data
 * stored in an item's Persistent Data Container (PDC).
 *
 * <p>Each block of attribute data is keyed by a source id, allowing multiple
 * plugins to attach independent attribute payloads to the same item without
 * clobbering one another.
 */
public final class PdcAttributeApi {

    private static volatile Bridge bridge;

    private PdcAttributeApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiAttribute's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiAttribute
     */
    public static void install(@NotNull Bridge bridge) {
        PdcAttributeApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    public static void uninstall(@Nullable Bridge bridge) {
        if (PdcAttributeApi.bridge == bridge) {
            PdcAttributeApi.bridge = null;
        }
    }

    /** {@return whether EmakiAttribute has installed its PDC API bridge} */
    public static boolean available() {
        return bridge != null;
    }

    /**
     * Registers a source id so payloads written under it are recognized.
     *
     * @param sourceId the source identifier to register
     * @return {@code true} if the source was newly registered
     */
    public static boolean registerSource(@NotNull String sourceId) {
        Bridge resolved = bridge;
        return resolved != null && resolved.registerSource(sourceId);
    }

    /**
     * Unregisters a previously registered source id.
     *
     * @param sourceId the source identifier to remove
     */
    public static void unregisterSource(@NotNull String sourceId) {
        Bridge resolved = bridge;
        if (resolved != null) {
            resolved.unregisterSource(sourceId);
        }
    }

    /**
     * Checks whether a source id is currently registered.
     *
     * @param sourceId the source identifier to check
     * @return {@code true} when the source is registered
     */
    public static boolean isRegisteredSource(@NotNull String sourceId) {
        Bridge resolved = bridge;
        return resolved != null && resolved.isRegisteredSource(sourceId);
    }

    /** {@return an immutable view of all currently registered source ids} */
    public static @NotNull Set<String> registeredSources() {
        Bridge resolved = bridge;
        return resolved == null ? Set.of() : resolved.registeredSources();
    }

    /**
     * Writes or replaces the attribute payload for its source onto the item.
     *
     * @param itemStack the target item; {@code null} is a no-op
     * @param payload the payload to store; {@code null} is a no-op
     * @return {@code true} if the item was modified
     */
    public static boolean write(@Nullable ItemStack itemStack, @Nullable PdcAttributePayload payload) {
        Bridge resolved = bridge;
        return resolved != null && resolved.write(itemStack, payload);
    }

    /**
     * Convenience overload that builds a payload from raw maps and writes it.
     *
     * @param itemStack the target item; {@code null} is a no-op
     * @param sourceId the source id the payload belongs to
     * @param attributes attribute id to value mapping; may be {@code null}
     * @param meta arbitrary string metadata; may be {@code null}
     * @return {@code true} if the item was modified
     */
    public static boolean write(@Nullable ItemStack itemStack,
            @NotNull String sourceId,
            @Nullable Map<String, Double> attributes,
            @Nullable Map<String, String> meta) {
        return write(itemStack, PdcAttributePayload.of(sourceId, attributes, meta));
    }

    /**
     * Reads the payload stored for a single source.
     *
     * @param itemStack the item to read from; {@code null} yields {@code null}
     * @param sourceId the source id whose payload to read
     * @return the stored payload, or {@code null} when absent or unavailable
     */
    public static @Nullable PdcAttributePayload read(@Nullable ItemStack itemStack, @NotNull String sourceId) {
        Bridge resolved = bridge;
        return resolved == null ? null : resolved.read(itemStack, sourceId);
    }

    /**
     * Reads every source payload stored on the item.
     *
     * @param itemStack the item to read from; {@code null} yields an empty map
     * @return a map of source id to payload; never {@code null}
     */
    public static @NotNull Map<String, PdcAttributePayload> readAll(@Nullable ItemStack itemStack) {
        Bridge resolved = bridge;
        return resolved == null ? Map.of() : resolved.readAll(itemStack);
    }

    /**
     * Removes the payload of a single source from the item.
     *
     * @param itemStack the target item; {@code null} is a no-op
     * @param sourceId the source id to clear
     * @return {@code true} if data was removed
     */
    public static boolean clear(@Nullable ItemStack itemStack, @NotNull String sourceId) {
        Bridge resolved = bridge;
        return resolved != null && resolved.clear(itemStack, sourceId);
    }

    /**
     * Removes all EmakiAttribute payloads from the item.
     *
     * @param itemStack the target item; {@code null} is a no-op
     */
    public static void clearAll(@Nullable ItemStack itemStack) {
        Bridge resolved = bridge;
        if (resolved != null) {
            resolved.clearAll(itemStack);
        }
    }

    /**
     * Copies every stored payload from one item to another, preserving each
     * payload in full and overwriting the destination per source id.
     *
     * <p>Sources listed in {@code excludedSourceIds} keep whatever the
     * destination item already holds.
     *
     * @param fromItem the item to read payloads from; {@code null} is a no-op
     * @param toItem the item to write payloads to; {@code null} is a no-op
     * @param excludedSourceIds source ids to skip; may be {@code null}
     */
    public static void copy(@Nullable ItemStack fromItem,
            @Nullable ItemStack toItem,
            @Nullable Set<String> excludedSourceIds) {
        Bridge resolved = bridge;
        if (resolved != null) {
            resolved.copy(fromItem, toItem, excludedSourceIds);
        }
    }

    /** Internal bridge installed by EmakiAttribute. */
    public interface Bridge {
        /**
         * Registers a source id so payloads written under it are recognized.
         *
         * @param sourceId the source identifier to register
         * @return {@code true} if the source was newly registered
         */
        boolean registerSource(@NotNull String sourceId);

        /**
         * Unregisters a previously registered source id.
         *
         * @param sourceId the source identifier to remove
         */
        void unregisterSource(@NotNull String sourceId);

        /**
         * Checks whether a source id is currently registered.
         *
         * @param sourceId the source identifier to check
         * @return {@code true} when the source is registered
         */
        boolean isRegisteredSource(@NotNull String sourceId);

        /** {@return an immutable view of all currently registered source ids} */
        @NotNull
        Set<String> registeredSources();

        /**
         * Writes or replaces the attribute payload for its source onto the item.
         *
         * @param itemStack the target item; may be {@code null}
         * @param payload the payload to store; may be {@code null}
         * @return {@code true} if the item was modified
         */
        boolean write(@Nullable ItemStack itemStack, @Nullable PdcAttributePayload payload);

        /**
         * Reads the payload stored for a single source.
         *
         * @param itemStack the item to read from; may be {@code null}
         * @param sourceId the source id whose payload to read
         * @return the stored payload, or {@code null} when absent
         */
        @Nullable
        PdcAttributePayload read(@Nullable ItemStack itemStack, @NotNull String sourceId);

        /**
         * Reads every source payload stored on the item.
         *
         * @param itemStack the item to read from; may be {@code null}
         * @return a map of source id to payload; never {@code null}
         */
        @NotNull
        Map<String, PdcAttributePayload> readAll(@Nullable ItemStack itemStack);

        /**
         * Removes the payload of a single source from the item.
         *
         * @param itemStack the target item; may be {@code null}
         * @param sourceId the source id to clear
         * @return {@code true} if data was removed
         */
        boolean clear(@Nullable ItemStack itemStack, @NotNull String sourceId);

        /**
         * Removes all EmakiAttribute payloads from the item.
         *
         * @param itemStack the target item; may be {@code null}
         */
        void clearAll(@Nullable ItemStack itemStack);

        /**
         * Copies every stored payload from one item to another, preserving each
         * payload in full and overwriting the destination per source id.
         *
         * @param fromItem the item to read payloads from; may be {@code null}
         * @param toItem the item to write payloads to; may be {@code null}
         * @param excludedSourceIds source ids to skip; may be {@code null}
         */
        void copy(@Nullable ItemStack fromItem,
                @Nullable ItemStack toItem,
                @Nullable Set<String> excludedSourceIds);
    }
}
