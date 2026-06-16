package emaki.jiuwu.craft.strengthen.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.strengthen.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.model.AttemptPreview;
import emaki.jiuwu.craft.strengthen.model.AttemptResult;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

/**
 * Static public API facade for inspecting and performing EmakiStrengthen
 * item-strengthening operations.
 *
 * <p>Third-party plugins should call these static methods directly. EmakiStrengthen
 * installs the backing bridge during its enable lifecycle and removes it on
 * disable.
 */
public final class EmakiStrengthenApi {

    private static volatile Bridge bridge;

    private EmakiStrengthenApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiStrengthen's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiStrengthen
     */
    public static void install(@NotNull Bridge bridge) {
        EmakiStrengthenApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiStrengthenApi.bridge == bridge) {
            EmakiStrengthenApi.bridge = null;
        }
    }

    /** {@return whether EmakiStrengthen has installed its API bridge} */
    public static boolean available() {
        return bridge != null;
    }

    /**
     * Checks whether the given item can be strengthened.
     *
     * @param itemStack the item to test; {@code null} yields {@code false}
     * @return {@code true} when the item is eligible for strengthening
     */
    public static boolean canStrengthen(@Nullable ItemStack itemStack) {
        Bridge resolved = bridge;
        return resolved != null && resolved.canStrengthen(itemStack);
    }

    /**
     * Reads the current strengthen state of an item.
     *
     * @param itemStack the item to inspect; {@code null} yields an ineligible state
     * @return the resolved state; never {@code null}
     */
    public static @NotNull StrengthenState readState(@Nullable ItemStack itemStack) {
        Bridge resolved = bridge;
        return resolved == null
                ? StrengthenState.ineligible("strengthen.error.api_unavailable", null, "")
                : resolved.readState(itemStack);
    }

    /**
     * Computes a non-committing preview of a strengthen attempt.
     *
     * @param player the player performing the attempt; may be {@code null}
     * @param context the attempt inputs; may be {@code null}
     * @return the preview describing cost, success rate and projected outcome; never {@code null}
     */
    public static @NotNull AttemptPreview preview(@Nullable Player player, @Nullable AttemptContext context) {
        Bridge resolved = bridge;
        return resolved == null
                ? unavailablePreview()
                : resolved.preview(player, context);
    }

    /**
     * Performs a strengthen attempt, consuming costs and materials.
     *
     * @param player the player performing the attempt; may be {@code null}
     * @param context the attempt inputs; may be {@code null}
     * @return the committed attempt result; never {@code null}
     */
    public static @NotNull AttemptResult attempt(@Nullable Player player, @Nullable AttemptContext context) {
        Bridge resolved = bridge;
        return resolved == null
                ? AttemptResult.failure("strengthen.error.api_unavailable", preview(player, context), java.util.Map.of())
                : resolved.attempt(player, context);
    }

    /**
     * Rebuilds the strengthen display layer of an item from its stored state.
     *
     * @param itemStack the item to rebuild; {@code null} yields {@code null}
     * @return the rebuilt item, the original item when unavailable, or {@code null}
     */
    public static @Nullable ItemStack rebuild(@Nullable ItemStack itemStack) {
        Bridge resolved = bridge;
        return resolved == null ? itemStack : resolved.rebuild(itemStack);
    }

    private static AttemptPreview unavailablePreview() {
        StrengthenState state = StrengthenState.ineligible("strengthen.error.api_unavailable", null, "");
        return new AttemptPreview(false, "strengthen.error.api_unavailable", state, null, 0, 0, 0D,
                List.of(), 0, 0, false, 0, Map.of(), Set.of(), List.of(), List.of());
    }

    /** Internal bridge installed by EmakiStrengthen. */
    public interface Bridge {
        /**
         * Checks whether the given item can be strengthened.
         *
         * @param itemStack the item to test; may be {@code null}
         * @return {@code true} when the item is eligible for strengthening
         */
        boolean canStrengthen(@Nullable ItemStack itemStack);

        /**
         * Reads the current strengthen state of an item.
         *
         * @param itemStack the item to inspect; may be {@code null}
         * @return the resolved state; never {@code null}
         */
        @NotNull
        StrengthenState readState(@Nullable ItemStack itemStack);

        /**
         * Computes a non-committing preview of a strengthen attempt.
         *
         * @param player the player performing the attempt; may be {@code null}
         * @param context the attempt inputs; may be {@code null}
         * @return the preview result; never {@code null}
         */
        @NotNull
        AttemptPreview preview(@Nullable Player player, @Nullable AttemptContext context);

        /**
         * Performs a strengthen attempt.
         *
         * @param player the player performing the attempt; may be {@code null}
         * @param context the attempt inputs; may be {@code null}
         * @return the attempt result; never {@code null}
         */
        @NotNull
        AttemptResult attempt(@Nullable Player player, @Nullable AttemptContext context);

        /**
         * Rebuilds the strengthen display layer of an item.
         *
         * @param itemStack the item to rebuild; may be {@code null}
         * @return the rebuilt item, or {@code null} when not applicable
         */
        @Nullable
        ItemStack rebuild(@Nullable ItemStack itemStack);
    }
}
