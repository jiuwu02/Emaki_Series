package emaki.jiuwu.craft.corelib.api;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for the shared EmakiCoreLib runtime core.
 *
 * <p>Third-party plugins should call these static methods directly. EmakiCoreLib
 * installs the backing bridge during its enable lifecycle and removes it on
 * disable.
 */
public final class EmakiCoreLibApi {

    private static volatile Bridge bridge;

    private EmakiCoreLibApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiCoreLib's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiCoreLib
     */
    public static void install(@NotNull Bridge bridge) {
        EmakiCoreLibApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiCoreLibApi.bridge == bridge) {
            EmakiCoreLibApi.bridge = null;
        }
    }

    /** {@return whether EmakiCoreLib has installed its API bridge} */
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
     * Resolves a unified display name for an item source shorthand or identifier.
     *
     * <p>The returned value is MiniMessage text and may contain translatable
     * components so the client can render vanilla names in its active language.
     *
     * @param itemSource item source shorthand such as {@code minecraft-apple},
     *                   {@code craftengine-namespace:id}, or another registered source
     * @return the resolved display name, or an empty string when unavailable
     */
    public static @NotNull String itemDisplayName(@Nullable String itemSource) {
        Bridge resolved = bridge;
        return resolved == null ? "" : resolved.itemDisplayName(itemSource);
    }

    /**
     * Resolves a unified display name for a real item stack.
     *
     * <p>The returned value is MiniMessage text and may contain translatable
     * components so the client can render vanilla names in its active language.
     *
     * @param itemStack item stack to inspect
     * @return the resolved display name, or an empty string when unavailable
     */
    public static @NotNull String itemDisplayName(@Nullable ItemStack itemStack) {
        Bridge resolved = bridge;
        return resolved == null ? "" : resolved.itemDisplayName(itemStack);
    }

    /** Internal bridge installed by EmakiCoreLib. */
    public interface Bridge {
        /** {@return the semantic version string of the backing plugin} */
        @NotNull
        String apiVersion();

        /** {@return the owning plugin's name} */
        @NotNull
        String pluginName();

        /** {@return whether the backing plugin is initialized and usable} */
        boolean isReady();

        /** {@return unified MiniMessage display name for an item source shorthand} */
        @NotNull
        String itemDisplayName(@Nullable String itemSource);

        /** {@return unified MiniMessage display name for a real item stack} */
        @NotNull
        String itemDisplayName(@Nullable ItemStack itemStack);
    }
}
