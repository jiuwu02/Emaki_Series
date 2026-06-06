package emaki.jiuwu.craft.gem.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for the EmakiGem gem/inlay system.
 */
public final class EmakiGemApi {

    private static volatile Bridge bridge;

    private EmakiGemApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiGem's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiGem
     */
    public static void install(@NotNull Bridge bridge) {
        EmakiGemApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiGemApi.bridge == bridge) {
            EmakiGemApi.bridge = null;
        }
    }

    /** {@return whether EmakiGem has installed its API bridge} */
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

    /** Internal bridge installed by EmakiGem. */
    public interface Bridge {
        /** {@return the semantic version string of the backing plugin} */
        @NotNull
        String apiVersion();

        /** {@return the owning plugin's name} */
        @NotNull
        String pluginName();

        /** {@return whether the backing plugin is initialized and usable} */
        boolean isReady();
    }
}
