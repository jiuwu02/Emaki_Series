package emaki.jiuwu.craft.level.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public facade for EmakiLevel.
 *
 * <p>Use {@link #catalog()} for read-only queries, {@link #operations()} for player-scoped writes,
 * and {@link #extensions()} for experience source registration. Every accessor is non-null and
 * degrades to an unavailable implementation when the runtime bridge is absent.
 */
public final class EmakiLevelApi {

    private static volatile Bridge bridge;

    private EmakiLevelApi() {
    }

    /** Installs the runtime bridge. Intended for EmakiLevel lifecycle code only. */
    @ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiLevelApi.bridge = bridge;
    }

    /** Removes the bridge only when it is still the active instance. */
    @ApiStatus.Internal
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiLevelApi.bridge == bridge) {
            EmakiLevelApi.bridge = null;
        }
    }

    /** {@return availability and identity metadata} */
    public static @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status() {
        Bridge resolved = bridge;
        return resolved == null
                ? emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled()
                : resolved.status();
    }

    /** {@return the read-only query layer; never {@code null}} */
    public static @NotNull LevelCatalog catalog() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableLevel.INSTANCE : resolved.catalog();
    }

    /** {@return the synchronous player operation layer; never {@code null}} */
    public static @NotNull LevelOperations operations() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableLevel.INSTANCE : resolved.operations();
    }

    /** {@return the experience-source extension layer; never {@code null}} */
    public static @NotNull LevelExtensions extensions() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableLevel.INSTANCE : resolved.extensions();
    }

    /** Runtime contract implemented by EmakiLevel; third-party plugins must not implement it. */
    @ApiStatus.NonExtendable
    public interface Bridge {

        @NotNull
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();

        @NotNull
        LevelCatalog catalog();

        @NotNull
        LevelOperations operations();

        @NotNull
        LevelExtensions extensions();
    }
}
