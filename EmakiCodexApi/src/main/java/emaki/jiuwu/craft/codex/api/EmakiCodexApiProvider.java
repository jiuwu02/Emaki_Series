package emaki.jiuwu.craft.codex.api;

/**
 * Compatibility helper for the static {@link EmakiCodexApi} facade.
 *
 * <p>The public API no longer exposes an instance service. Use
 * {@link EmakiCodexApi} static methods directly and this provider only for
 * availability checks during migration.
 */
public final class EmakiCodexApiProvider {

    private EmakiCodexApiProvider() {
    }

    /**
     * Checks whether the static EmakiCodex API bridge is installed.
     *
     * @return {@code true} when EmakiCodex is available
     */
    public static boolean available() {
        return EmakiCodexApi.available();
    }

    /**
     * Verifies that EmakiCodex is available.
     *
     * @throws IllegalStateException when the static API bridge is not installed
     */
    public static void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("EmakiCodex API is not available.");
        }
    }
}
