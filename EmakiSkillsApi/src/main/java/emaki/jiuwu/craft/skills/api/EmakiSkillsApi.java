package emaki.jiuwu.craft.skills.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Static public facade for EmakiSkills. Accessors never return {@code null}. */
public final class EmakiSkillsApi {

    private static volatile Bridge bridge;

    private EmakiSkillsApi() {
    }

    @ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiSkillsApi.bridge = bridge;
    }

    @ApiStatus.Internal
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiSkillsApi.bridge == bridge) {
            EmakiSkillsApi.bridge = null;
        }
    }

    public static @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status() {
        Bridge resolved = bridge;
        return resolved == null
                ? emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled()
                : resolved.status();
    }

    public static @NotNull SkillCatalog catalog() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableSkills.CATALOG : resolved.catalog();
    }

    public static @NotNull SkillOperations operations() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableSkills.OPERATIONS : resolved.operations();
    }

    public static @NotNull SkillExtensions extensions() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableSkills.EXTENSIONS : resolved.extensions();
    }

    /** Bridge contract implemented only by EmakiSkills. */
    @ApiStatus.NonExtendable
    public interface Bridge {
        @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();
        @NotNull SkillCatalog catalog();
        @NotNull SkillOperations operations();
        @NotNull SkillExtensions extensions();
    }
}
