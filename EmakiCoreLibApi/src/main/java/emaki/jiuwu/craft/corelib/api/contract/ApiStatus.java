package emaki.jiuwu.craft.corelib.api.contract;

import org.jetbrains.annotations.NotNull;

/**
 * Availability and identity metadata for an Emaki API facade.
 *
 * <p>{@link #installed()} only means the bridge exists; business calls require {@link #usable()}.
 * Version fields are diagnostic only and must not gate optional features.
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
