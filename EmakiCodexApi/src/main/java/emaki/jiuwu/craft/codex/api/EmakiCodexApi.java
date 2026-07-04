package emaki.jiuwu.craft.codex.api;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for the EmakiCodex advancement system.
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

        /** Grants an advancement to an online player. */
        boolean grantAdvancement(@NotNull UUID player, @NotNull String advancementId);

        /** Revokes an advancement from an online player. */
        boolean revokeAdvancement(@NotNull UUID player, @NotNull String advancementId);
    }
}
