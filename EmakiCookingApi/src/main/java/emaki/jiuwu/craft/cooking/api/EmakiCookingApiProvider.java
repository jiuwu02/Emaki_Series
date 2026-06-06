package emaki.jiuwu.craft.cooking.api;

/**
 * Compatibility helper for the static {@link EmakiCookingApi} facade.
 *
 * <p>The public API no longer exposes an instance service. Use
 * {@link EmakiCookingApi} static methods directly and this provider only for
 * availability checks during migration.
 */
public final class EmakiCookingApiProvider {

    private EmakiCookingApiProvider() {
    }

    /**
     * Checks whether the static EmakiCooking API bridge is installed.
     *
     * @return {@code true} when EmakiCooking is available
     */
    public static boolean available() {
        return EmakiCookingApi.available();
    }

    /**
     * Verifies that EmakiCooking is available.
     *
     * @throws IllegalStateException when the static API bridge is not installed
     */
    public static void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("EmakiCooking API is not available.");
        }
    }
}
