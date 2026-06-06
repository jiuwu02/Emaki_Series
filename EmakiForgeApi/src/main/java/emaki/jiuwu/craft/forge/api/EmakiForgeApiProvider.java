package emaki.jiuwu.craft.forge.api;

/**
 * Compatibility helper for the static {@link EmakiForgeApi} facade.
 *
 * <p>The public API no longer exposes an instance service. Use
 * {@link EmakiForgeApi} static methods directly and this provider only for
 * availability checks during migration.
 */
public final class EmakiForgeApiProvider {

    private EmakiForgeApiProvider() {
    }

    /**
     * Checks whether the static EmakiForge API bridge is installed.
     *
     * @return {@code true} when EmakiForge is available
     */
    public static boolean available() {
        return EmakiForgeApi.available();
    }

    /**
     * Verifies that EmakiForge is available.
     *
     * @throws IllegalStateException when the static API bridge is not installed
     */
    public static void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("EmakiForge API is not available.");
        }
    }
}
