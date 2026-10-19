package emaki.jiuwu.craft.dungeon.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for EmakiDungeon.
 *
 * <p>EmakiDungeon exposes dungeon entry, party, instance, script, and editor
 * facades. The M0 skeleton only installs the availability bridge; the five
 * domain facades are delivered in later phases. Accessors never return
 * {@code null}: while EmakiDungeon is absent the facade reports
 * {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#notInstalled()}.
 *
 * <p>Resolve accessors at the point of use rather than caching them in a field,
 * because the backing bridge is replaced across a reload.
 */
public final class EmakiDungeonApi {

    private static volatile Bridge bridge;

    private EmakiDungeonApi() {
    }

    /** Installs the backing bridge. For EmakiDungeon lifecycle use only. */
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
     * {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#notInstalled()} when no bridge is installed}
     */
    public static @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status() {
        Bridge resolved = bridge;
        if (resolved == null) {
            return emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled();
        }
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus s = resolved.status();
        return s == null ? emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled() : s;
    }

    /** Bridge contract implemented by EmakiDungeon runtime. Third-party plugins must not implement it. */
    @ApiStatus.NonExtendable
    public interface Bridge {

        /** {@return availability and identity metadata; must never be {@code null}} */
        @NotNull
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();
    }
}
