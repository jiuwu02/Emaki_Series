package emaki.jiuwu.craft.strengthen.api;

/**
 * Compatibility helper for the static {@link EmakiStrengthenApi} facade.
 *
 * <p>The public API no longer exposes an instance service. Use
 * {@link EmakiStrengthenApi} static methods directly and this provider only for
 * availability checks during migration.
 */
public final class EmakiStrengthenApiProvider {

    private EmakiStrengthenApiProvider() {
    }

    /**
     * Checks whether the static EmakiStrengthen API bridge is installed.
     *
     * @return {@code true} when EmakiStrengthen is available
     */
    public static boolean available() {
        return EmakiStrengthenApi.available();
    }

    /**
     * Verifies that EmakiStrengthen is available.
     *
     * @throws IllegalStateException when the static API bridge is not installed
     */
    public static void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("EmakiStrengthen API is not available.");
        }
    }
}
