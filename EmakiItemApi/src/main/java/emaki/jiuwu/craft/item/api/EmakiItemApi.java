package emaki.jiuwu.craft.item.api;

import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for creating and identifying EmakiItem custom items.
 *
 * <p>Third-party plugins should call these static methods directly. EmakiItem
 * installs the backing bridge during its enable lifecycle and removes it on
 * disable.
 */
public final class EmakiItemApi {

    private static volatile Bridge bridge;

    private EmakiItemApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiItem's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiItem
     */
    public static void install(@NotNull Bridge bridge) {
        EmakiItemApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiItemApi.bridge == bridge) {
            EmakiItemApi.bridge = null;
        }
    }

    /** {@return whether EmakiItem has installed its API bridge} */
    public static boolean available() {
        return bridge != null;
    }

    /**
     * Checks whether an item definition is loaded.
     *
     * @param id the item definition id
     * @return {@code true} when the definition exists
     */
    public static boolean exists(@NotNull String id) {
        Bridge resolved = bridge;
        return resolved != null && resolved.exists(id);
    }

    /**
     * Builds a fresh item stack from a definition.
     *
     * @param id the item definition id
     * @param amount the desired stack size
     * @return the created item stack, or {@code null} when unavailable or unknown
     */
    public static @Nullable ItemStack create(@NotNull String id, int amount) {
        Bridge resolved = bridge;
        return resolved == null ? null : resolved.create(id, amount);
    }

    /**
     * Identifies the EmakiItem definition behind an existing stack.
     *
     * @param itemStack the stack to inspect; may be {@code null}
     * @return the definition id, or {@code null} when the stack is not an EmakiItem item
     */
    public static @Nullable String identify(@Nullable ItemStack itemStack) {
        Bridge resolved = bridge;
        return resolved == null ? null : resolved.identify(itemStack);
    }

    /** {@return an immutable view of all loaded item definition ids} */
    public static @NotNull Set<String> definitionIds() {
        Bridge resolved = bridge;
        return resolved == null ? Set.of() : resolved.definitionIds();
    }

    /**
     * Returns the configured display name for a definition.
     *
     * @param id the item definition id
     * @return the display name, or an empty string when unavailable or unknown
     */
    public static @NotNull String displayName(@NotNull String id) {
        Bridge resolved = bridge;
        return resolved == null ? "" : resolved.displayName(id);
    }

    /** Internal bridge installed by EmakiItem. */
    public interface Bridge {
        /**
         * Checks whether an item definition is loaded.
         *
         * @param id the item definition id
         * @return {@code true} when the definition exists
         */
        boolean exists(@NotNull String id);

        /**
         * Builds a fresh item stack from a definition.
         *
         * @param id the item definition id
         * @param amount the desired stack size
         * @return the created item stack, or {@code null} when the id is unknown
         */
        @Nullable
        ItemStack create(@NotNull String id, int amount);

        /**
         * Identifies the EmakiItem definition behind an existing stack.
         *
         * @param itemStack the stack to inspect; may be {@code null}
         * @return the definition id, or {@code null} when absent
         */
        @Nullable
        String identify(@Nullable ItemStack itemStack);

        /** {@return an immutable view of all loaded item definition ids} */
        @NotNull
        Set<String> definitionIds();

        /**
         * Returns the configured display name for a definition.
         *
         * @param id the item definition id
         * @return the display name, or an empty string when unknown
         */
        @NotNull
        String displayName(@NotNull String id);
    }
}
