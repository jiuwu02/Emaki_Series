package emaki.jiuwu.craft.skills.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for the EmakiSkills skill-scripting system.
 *
 * <p>Third-party plugins can use this facade to check whether EmakiSkills is
 * available and to access the skill-script action registry when it is installed.
 */
public final class EmakiSkillsApi {

    private static volatile Bridge bridge;

    private EmakiSkillsApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiSkills' lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiSkills
     */
    public static void install(@NotNull Bridge bridge) {
        EmakiSkillsApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiSkillsApi.bridge == bridge) {
            EmakiSkillsApi.bridge = null;
        }
    }

    /** {@return whether EmakiSkills has installed its API bridge} */
    public static boolean available() {
        return bridge != null;
    }

    /**
     * Returns the registry used to register custom skill-script actions.
     *
     * @return the registry, or {@code null} when EmakiSkills is unavailable
     */
    public static @Nullable SkillScriptActionRegistry scriptActionRegistry() {
        Bridge resolved = bridge;
        return resolved == null ? null : resolved.scriptActionRegistry();
    }

    /** Internal bridge installed by EmakiSkills. */
    public interface Bridge {
        /**
         * Returns the registry used to register custom skill-script actions.
         *
         * @return the registry, or {@code null} when the registry is not initialized
         */
        @Nullable
        SkillScriptActionRegistry scriptActionRegistry();
    }
}
