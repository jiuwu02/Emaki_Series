package emaki.jiuwu.craft.codex.api;

import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for the EmakiCodex recipe-bridge and advancement system.
 *
 * <p>Third-party plugins depend on this API jar (never the implementation jar) and
 * call these static methods. EmakiCodex installs the backing {@link Bridge} during
 * its own lifecycle; when EmakiCodex is absent every method degrades gracefully.
 */
public final class EmakiCodexApi {

    private static volatile Bridge bridge;

    private EmakiCodexApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiCodex's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiCodex
     */
    public static void install(@NotNull Bridge bridge) {
        EmakiCodexApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiCodexApi.bridge == bridge) {
            EmakiCodexApi.bridge = null;
        }
    }

    /** {@return whether EmakiCodex has installed its API bridge} */
    public static boolean available() {
        return bridge != null;
    }

    /** {@return the semantic version string of this API, or an empty string when unavailable} */
    public static @NotNull String apiVersion() {
        Bridge resolved = bridge;
        return resolved == null ? "" : resolved.apiVersion();
    }

    /** {@return the owning plugin's name, or an empty string when unavailable} */
    public static @NotNull String pluginName() {
        Bridge resolved = bridge;
        return resolved == null ? "" : resolved.pluginName();
    }

    /** {@return whether the plugin has finished initializing and is usable} */
    public static boolean isReady() {
        Bridge resolved = bridge;
        return resolved != null && resolved.isReady();
    }

    /**
     * Unlocks a recipe for a player so it becomes visible in their recipe viewer.
     *
     * @param player  the target player uuid
     * @param recipeId the recipe id (namespaced key string, e.g. {@code minecraft:diamond_sword})
     * @return {@code true} when the unlock was recorded; {@code false} when unavailable
     */
    public static boolean unlockRecipe(@NotNull UUID player, @NotNull String recipeId) {
        Bridge resolved = bridge;
        return resolved != null && resolved.unlockRecipe(player, recipeId);
    }

    /**
     * Locks (hides) a previously unlocked recipe for a player.
     *
     * @param player   the target player uuid
     * @param recipeId the recipe id
     * @return {@code true} when the lock was recorded; {@code false} when unavailable
     */
    public static boolean lockRecipe(@NotNull UUID player, @NotNull String recipeId) {
        Bridge resolved = bridge;
        return resolved != null && resolved.lockRecipe(player, recipeId);
    }

    /**
     * Checks whether a recipe is currently visible to a player under the active
     * visibility rules (default-unlock-all, whitelist, per-player unlock records).
     *
     * @param player   the target player uuid
     * @param recipeId the recipe id
     * @return {@code true} when the recipe is visible to the player
     */
    public static boolean isRecipeVisible(@NotNull UUID player, @NotNull String recipeId) {
        Bridge resolved = bridge;
        return resolved != null && resolved.isRecipeVisible(player, recipeId);
    }

    /** {@return the set of recipe ids the player has explicitly unlocked, or an empty set} */
    public static @NotNull Set<String> unlockedRecipes(@NotNull UUID player) {
        Bridge resolved = bridge;
        return resolved == null ? Set.of() : resolved.unlockedRecipes(player);
    }

    /**
     * Grants an advancement to an online player by awarding its manual criterion.
     *
     * @param player        the target player uuid (must be online)
     * @param advancementId the advancement id registered by EmakiCodex
     * @return {@code true} when the criterion was awarded
     */
    public static boolean grantAdvancement(@NotNull UUID player, @NotNull String advancementId) {
        Bridge resolved = bridge;
        return resolved != null && resolved.grantAdvancement(player, advancementId);
    }

    /**
     * Revokes an advancement from an online player.
     *
     * @param player        the target player uuid (must be online)
     * @param advancementId the advancement id registered by EmakiCodex
     * @return {@code true} when the criterion was revoked
     */
    public static boolean revokeAdvancement(@NotNull UUID player, @NotNull String advancementId) {
        Bridge resolved = bridge;
        return resolved != null && resolved.revokeAdvancement(player, advancementId);
    }

    /** Internal bridge installed by EmakiCodex. */
    public interface Bridge {
        /** {@return the semantic version string of the backing plugin} */
        @NotNull
        String apiVersion();

        /** {@return the owning plugin's name} */
        @NotNull
        String pluginName();

        /** {@return whether the backing plugin is initialized and usable} */
        boolean isReady();

        /** Records a recipe unlock for the player. */
        boolean unlockRecipe(@NotNull UUID player, @NotNull String recipeId);

        /** Removes a recipe unlock for the player. */
        boolean lockRecipe(@NotNull UUID player, @NotNull String recipeId);

        /** {@return whether the recipe is visible to the player} */
        boolean isRecipeVisible(@NotNull UUID player, @NotNull String recipeId);

        /** {@return the recipe ids the player has explicitly unlocked} */
        @NotNull
        Set<String> unlockedRecipes(@NotNull UUID player);

        /** Grants an advancement to an online player. */
        boolean grantAdvancement(@NotNull UUID player, @NotNull String advancementId);

        /** Revokes an advancement from an online player. */
        boolean revokeAdvancement(@NotNull UUID player, @NotNull String advancementId);
    }
}
