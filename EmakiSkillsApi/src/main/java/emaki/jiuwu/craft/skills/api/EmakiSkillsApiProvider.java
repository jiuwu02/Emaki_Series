package emaki.jiuwu.craft.skills.api;

/**
 * Compatibility helper for the static {@link EmakiSkillsApi} facade.
 *
 * <p>The public API no longer exposes an instance service. Use
 * {@link EmakiSkillsApi} static methods directly and this provider only for
 * availability checks during migration.
 */
public final class EmakiSkillsApiProvider {

    private EmakiSkillsApiProvider() {
    }

    /**
     * Checks whether the static EmakiSkills API bridge is installed.
     *
     * @return {@code true} when EmakiSkills is available
     */
    public static boolean available() {
        return EmakiSkillsApi.available();
    }

    /**
     * Verifies that EmakiSkills is available.
     *
     * @throws IllegalStateException when the static API bridge is not installed
     */
    public static void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("EmakiSkills API is not available.");
        }
    }
}
