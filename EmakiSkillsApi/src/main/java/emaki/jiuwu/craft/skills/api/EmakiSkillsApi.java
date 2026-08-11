package emaki.jiuwu.craft.skills.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public facade for EmakiSkills.
 *
 * <p>Use {@link #catalog()} for read-only definition and player queries, {@link #operations()} for casting
 * and state changes, and {@link #extensions()} to register an external skill source. Every accessor is
 * non-null and degrades to a stable unavailable implementation when the runtime bridge is absent, so callers
 * must test {@link #status()} or classify on {@code FailureKind} instead of catching
 * {@code NullPointerException}.
 *
 * <p>Accessors are cheap and the backing bridge is replaced across a reload, so resolve the layer at the
 * point of use rather than caching it in a field.
 */
public final class EmakiSkillsApi {

    private static volatile Bridge bridge;

    private EmakiSkillsApi() {
    }

    /**
     * Installs the runtime bridge. Intended for EmakiSkills lifecycle code only.
     *
     * @param bridge the runtime implementation to publish
     */
    @ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiSkillsApi.bridge = bridge;
    }

    /**
     * Removes the bridge only when it is still the active instance, so a stale instance from a previous
     * reload cannot uninstall its replacement.
     *
     * @param bridge the instance attempting to uninstall; a non-matching or {@code null} value is ignored
     */
    @ApiStatus.Internal
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiSkillsApi.bridge == bridge) {
            EmakiSkillsApi.bridge = null;
        }
    }

    /** {@return availability and identity metadata, reporting not-installed while no bridge is present} */
    public static @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status() {
        Bridge resolved = bridge;
        return resolved == null
                ? emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled()
                : resolved.status();
    }

    /**
     * {@return the read-only query layer; never {@code null}, falling back to an unavailable implementation
     * whose per-player queries fail with {@code UNAVAILABLE} and whose collections are empty}
     */
    public static @NotNull SkillCatalog catalog() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableSkills.CATALOG : resolved.catalog();
    }

    /**
     * {@return the state-changing operation layer; never {@code null}, falling back to an unavailable
     * implementation whose methods all fail with {@code UNAVAILABLE} without touching game state}
     */
    public static @NotNull SkillOperations operations() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableSkills.OPERATIONS : resolved.operations();
    }

    /**
     * {@return the extension registration layer; never {@code null}, falling back to an unavailable
     * implementation that hands back inactive no-op registration handles}
     */
    public static @NotNull SkillExtensions extensions() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableSkills.EXTENSIONS : resolved.extensions();
    }

    /** Runtime contract implemented only by EmakiSkills; third-party plugins must not implement it. */
    @ApiStatus.NonExtendable
    public interface Bridge {

        /** {@return availability and identity metadata for the running EmakiSkills instance} */
        @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();

        /** {@return the runtime read-only query layer} */
        @NotNull SkillCatalog catalog();

        /** {@return the runtime state-changing operation layer} */
        @NotNull SkillOperations operations();

        /** {@return the runtime extension registration layer} */
        @NotNull SkillExtensions extensions();
    }
}
