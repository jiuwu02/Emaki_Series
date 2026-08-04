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

    /**
     * Installs the runtime bridge. Intended for EmakiLevel lifecycle code only.
     *
     * @param bridge the runtime implementation to publish; replaces any previously installed bridge
     */
    @ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiLevelApi.bridge = bridge;
    }

    /**
     * Removes the bridge only when it is still the active instance.
     *
     * <p>Passing a superseded instance is a no-op, so a late uninstall from an old plugin instance
     * cannot detach the bridge published by a newer one after a reload.
     *
     * @param bridge the instance to retire; ignored when it is not the currently active bridge, and
     *               {@code null} never detaches an active bridge
     */
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

        /** {@return availability and identity metadata for the live runtime} */
        @NotNull
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();

        /** {@return the runtime's read-only query layer} */
        @NotNull
        LevelCatalog catalog();

        /** {@return the runtime's synchronous player operation layer} */
        @NotNull
        LevelOperations operations();

        /** {@return the runtime's experience-source extension layer} */
        @NotNull
        LevelExtensions extensions();
    }
}
