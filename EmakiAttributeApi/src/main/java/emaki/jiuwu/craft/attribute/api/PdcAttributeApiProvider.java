package emaki.jiuwu.craft.attribute.api;

/**
 * Compatibility helper for the static {@link PdcAttributeApi} facade.
 *
 * <p>The public API no longer exposes an instance service. Use
 * {@link PdcAttributeApi} static methods directly and this provider only for
 * availability checks during migration.
 */
public final class PdcAttributeApiProvider {

    private PdcAttributeApiProvider() {
    }

    /**
     * Checks whether the static EmakiAttribute PDC API bridge is installed.
     *
     * @return {@code true} when EmakiAttribute PDC API is available
     */
    public static boolean available() {
        return PdcAttributeApi.available();
    }

    /**
     * Verifies that EmakiAttribute PDC API is available.
     *
     * @throws IllegalStateException when the static API bridge is not installed
     */
    public static void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("EmakiAttribute PDC API is not available.");
        }
    }
}
