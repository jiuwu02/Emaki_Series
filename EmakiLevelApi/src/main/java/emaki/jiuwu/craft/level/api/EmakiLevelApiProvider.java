package emaki.jiuwu.craft.level.api;

/**
 * Compatibility helper for the static {@link EmakiLevelApi} facade.
 *
 * <p>The public API no longer exposes an instance service. Use
 * {@link EmakiLevelApi} static methods directly and this provider only for
 * availability checks during migration.
 */
public final class EmakiLevelApiProvider {

    private EmakiLevelApiProvider() {
    }

    /**
     * Checks whether the static EmakiLevel API bridge is installed.
     *
     * @return {@code true} when EmakiLevel is available
     */
    public static boolean available() {
        return EmakiLevelApi.available();
    }

    /**
     * Verifies that EmakiLevel is available.
     *
     * @throws IllegalStateException when the static API bridge is not installed
     */
    public static void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("EmakiLevel API is not available.");
        }
    }
}
