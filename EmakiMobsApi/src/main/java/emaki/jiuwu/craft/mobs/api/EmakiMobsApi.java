package emaki.jiuwu.craft.mobs.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for EmakiMobs.
 *
 * <p>Use {@link #status()} to check availability and {@link #catalog()} to query
 * registered mob definitions. Accessors never return {@code null}: while EmakiMobs
 * is absent the catalog returns empty answers, so callers must not treat a
 * {@link NullPointerException} as an availability signal.
 *
 * <p>Resolve the accessors at the point of use rather than caching them in a field,
 * because the backing bridge is replaced across a reload.
 */
public final class EmakiMobsApi {

    private static volatile Bridge bridge;

    private EmakiMobsApi() {
    }

    /** Installs the backing bridge. For EmakiMobs lifecycle use only. */
    @ApiStatus.Internal
    public static void install(@NotNull Bridge b) {
        bridge = b;
    }

    /** Removes the backing bridge when it is still the active bridge. */
    @ApiStatus.Internal
    public static void uninstall(@Nullable Bridge b) {
        if (bridge == b) {
            bridge = null;
        }
    }

    /**
     * {@return availability and identity metadata; never {@code null}, and
     * {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#notInstalled()} when absent}
     */
    public static @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status() {
        Bridge resolved = bridge;
        if (resolved == null) {
            return emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled();
        }
        var s = resolved.status();
        return s == null ? emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled() : s;
    }

    /**
     * {@return the mob definition catalog; never {@code null}, and an empty-answer
     * implementation while no bridge is installed}
     */
    public static @NotNull MobCatalog catalog() {
        Bridge resolved = bridge;
        if (resolved == null) {
            return UnavailableMobs.CATALOG;
        }
        var c = resolved.catalog();
        return c == null ? UnavailableMobs.CATALOG : c;
    }

    /** Bridge contract implemented by EmakiMobs runtime. Third-party plugins must not implement it. */
    @ApiStatus.NonExtendable
    public interface Bridge {

        /** {@return availability and identity metadata; must never be {@code null}} */
        @NotNull
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();

        /** {@return the mob definition catalog; must never be {@code null}} */
        @NotNull
        MobCatalog catalog();
    }
}
