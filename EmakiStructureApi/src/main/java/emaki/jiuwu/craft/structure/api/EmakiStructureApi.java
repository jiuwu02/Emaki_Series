package emaki.jiuwu.craft.structure.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for EmakiStructure.
 *
 * <p>Use {@link #status()} to check availability and {@link #catalog()} to query the loaded
 * structure templates. Accessors never return {@code null}: while EmakiStructure is absent the
 * catalog returns empty answers, so callers must not treat a {@link NullPointerException} as an
 * availability signal.
 *
 * <p>Resolve the accessors at the point of use rather than caching them in a field, because the
 * backing bridge is replaced across a reload.
 */
public final class EmakiStructureApi {

    private static volatile Bridge bridge;

    private EmakiStructureApi() {
    }

    /** Installs the backing bridge. For EmakiStructure lifecycle use only. */
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
     * {@return availability and identity metadata; never {@code null}, and a not-installed
     * status while absent}
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
     * {@return the structure template catalog; never {@code null}, and an empty-answer
     * implementation while no bridge is installed}
     */
    public static @NotNull StructureCatalog catalog() {
        Bridge resolved = bridge;
        if (resolved == null) {
            return UnavailableStructure.CATALOG;
        }
        var c = resolved.catalog();
        return c == null ? UnavailableStructure.CATALOG : c;
    }

    /** Bridge contract implemented by EmakiStructure runtime. Third-party plugins must not implement it. */
    @ApiStatus.NonExtendable
    public interface Bridge {

        /** {@return availability and identity metadata; must never be {@code null}} */
        @NotNull
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();

        /** {@return the structure template catalog; must never be {@code null}} */
        @NotNull
        StructureCatalog catalog();
    }
}
