package emaki.jiuwu.craft.item.api;

/**
 * Compatibility helper for the static {@link EmakiItemApi} facade.
 *
 * <p>The public API no longer exposes an instance service. Use
 * {@link EmakiItemApi} static methods directly and this provider only for
 * availability checks during migration.
 */
public final class EmakiItemApiProvider {

    private EmakiItemApiProvider() {
    }

    /**
     * Checks whether the static EmakiItem API bridge is installed.
     *
     * @return {@code true} when EmakiItem is available
     */
    public static boolean available() {
        return EmakiItemApi.available();
    }

    /**
     * Verifies that EmakiItem is available.
     *
     * @throws IllegalStateException when the static API bridge is not installed
     */
    public static void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("EmakiItem API is not available.");
        }
    }
}
