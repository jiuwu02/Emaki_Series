package emaki.jiuwu.craft.gem.api;

/**
 * Compatibility helper for the static {@link EmakiGemApi} facade.
 *
 * <p>The public API no longer exposes an instance service. Use
 * {@link EmakiGemApi} static methods directly and this provider only for
 * availability checks during migration.
 */
public final class EmakiGemApiProvider {

    private EmakiGemApiProvider() {
    }

    /**
     * Checks whether the static EmakiGem API bridge is installed.
     *
     * @return {@code true} when EmakiGem is available
     */
    public static boolean available() {
        return EmakiGemApi.available();
    }

    /**
     * Verifies that EmakiGem is available.
     *
     * @throws IllegalStateException when the static API bridge is not installed
     */
    public static void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("EmakiGem API is not available.");
        }
    }
}
