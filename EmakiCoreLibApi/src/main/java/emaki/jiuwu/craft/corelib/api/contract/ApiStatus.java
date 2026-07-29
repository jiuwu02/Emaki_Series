package emaki.jiuwu.craft.corelib.api.contract;

import org.jetbrains.annotations.NotNull;

/**
 * Uniform availability and identity metadata for an Emaki API facade.
 *
 * <p>This record replaces the three historic detection names ({@code available()},
 * {@code isAvailable()}, {@code isReady()}) and the two metadata accessors ({@code apiVersion()},
 * {@code pluginName()}) with one value object.
 *
 * <p>{@link #installed()} and {@link #ready()} are deliberately separate. A bridge may be installed
 * early in the owning plugin's enable sequence while its subsystems (recipe tables, registries, data
 * stores) are still loading. Business calls are only safe once {@link #usable()} is {@code true}.
 *
 * <p>{@link #apiVersion()} is diagnostic only. Never gate behaviour on it: this project forbids
 * hard-coded cross-plugin version comparisons.
 *
 * @param installed     whether the owning plugin has installed its API bridge
 * @param ready         whether the owning plugin's subsystems have finished loading
 * @param pluginName    the owning plugin's name, or an empty string when not installed
 * @param pluginVersion the owning plugin's version, or an empty string when not installed
 * @param apiVersion    the API artifact's version, or an empty string when not installed
 */
public record ApiStatus(boolean installed,
                        boolean ready,
                        @NotNull String pluginName,
                        @NotNull String pluginVersion,
                        @NotNull String apiVersion) {

    private static final ApiStatus NOT_INSTALLED = new ApiStatus(false, false, "", "", "");

    /**
     * Creates a status value.
     *
     * @param installed     whether the owning plugin has installed its API bridge
     * @param ready         whether the owning plugin's subsystems have finished loading
     * @param pluginName    the owning plugin's name
     * @param pluginVersion the owning plugin's version
     * @param apiVersion    the API artifact's version
     * @throws NullPointerException when any string argument is {@code null}
     */
    public ApiStatus {
        if (pluginName == null) {
            throw new NullPointerException("pluginName");
        }
        if (pluginVersion == null) {
            throw new NullPointerException("pluginVersion");
        }
        if (apiVersion == null) {
            throw new NullPointerException("apiVersion");
        }
    }

    /**
     * {@return whether the bridge is installed and its subsystems are ready, meaning business calls
     * can be attempted}
     */
    public boolean usable() {
        return installed && ready;
    }

    /** {@return the shared status value used when no bridge is installed} */
    public static @NotNull ApiStatus notInstalled() {
        return NOT_INSTALLED;
    }

    /**
     * Creates a status for a bridge that is installed and whose subsystems are ready.
     *
     * @param pluginName    the owning plugin's name
     * @param pluginVersion the owning plugin's version
     * @param apiVersion    the API artifact's version
     * @return a usable status
     */
    public static @NotNull ApiStatus ready(@NotNull String pluginName,
                                           @NotNull String pluginVersion,
                                           @NotNull String apiVersion) {
        return new ApiStatus(true, true, pluginName, pluginVersion, apiVersion);
    }

    /**
     * Creates a status for a bridge that is installed but whose subsystems are still loading.
     *
     * @param pluginName    the owning plugin's name
     * @param pluginVersion the owning plugin's version
     * @param apiVersion    the API artifact's version
     * @return an installed but not-yet-usable status
     */
    public static @NotNull ApiStatus loading(@NotNull String pluginName,
                                             @NotNull String pluginVersion,
                                             @NotNull String apiVersion) {
        return new ApiStatus(true, false, pluginName, pluginVersion, apiVersion);
    }
}
