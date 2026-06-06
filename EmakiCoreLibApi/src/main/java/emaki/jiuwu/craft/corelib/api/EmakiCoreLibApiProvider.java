package emaki.jiuwu.craft.corelib.api;

/**
 * Compatibility helper for the static {@link EmakiCoreLibApi} facade.
 *
 * <p>The public API no longer exposes an instance service. Use
 * {@link EmakiCoreLibApi} static methods directly and this provider only for
 * availability checks during migration.
 */
public final class EmakiCoreLibApiProvider {

    private EmakiCoreLibApiProvider() {
    }

    /**
     * Checks whether the static EmakiCoreLib API bridge is installed.
     *
     * @return {@code true} when EmakiCoreLib is available
     */
    public static boolean available() {
        return EmakiCoreLibApi.available();
    }

    /**
     * Verifies that EmakiCoreLib is available.
     *
     * @throws IllegalStateException when the static API bridge is not installed
     */
    public static void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("EmakiCoreLib API is not available.");
        }
    }
}
