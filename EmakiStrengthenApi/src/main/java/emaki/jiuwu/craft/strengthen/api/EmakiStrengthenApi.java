package emaki.jiuwu.craft.strengthen.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;

/**
 * Static public API facade for the EmakiStrengthen strengthening system.
 *
 * <p>Capabilities are grouped behind {@link #mastery()} for provider-owned item mastery snapshots,
 * {@link #catalog()} for strengthening queries, and {@link #operations()} for state-changing work.
 * All accessors are non-null. When the runtime bridge
 * is absent, catalog collections are empty and result-bearing calls return
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}.
 */
public final class EmakiStrengthenApi {

    private static volatile Bridge bridge;

    private EmakiStrengthenApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiStrengthen's lifecycle only.
     *
     * @param bridge the active runtime bridge
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiStrengthenApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still active.
     *
     * @param bridge the bridge to remove
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiStrengthenApi.bridge == bridge) {
            EmakiStrengthenApi.bridge = null;
        }
    }

    /**
     * {@return availability and identity metadata; never {@code null}, and
     * {@link ApiStatus#notInstalled()} when no bridge is installed}
     */
    public static @NotNull ApiStatus status() {
        Bridge resolved = bridge;
        return resolved == null ? ApiStatus.notInstalled() : resolved.status();
    }

    /**
     * {@return the read-only item mastery layer; never {@code null}, and an unavailable implementation
     * when the bridge or its mastery provider is absent}
     */
    public static @NotNull ItemMastery mastery() {
        Bridge resolved = bridge;
        if (resolved == null) {
            return UnavailableStrengthen.MASTERY;
        }
        ItemMastery mastery = resolved.mastery();
        return mastery == null ? UnavailableStrengthen.MASTERY : mastery;
    }

    /** {@return the read-only catalog layer; never {@code null}} */
    public static @NotNull StrengthenCatalog catalog() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableStrengthen.CATALOG : resolved.catalog();
    }

    /** {@return the state-changing operation layer; never {@code null}} */
    public static @NotNull StrengthenOperations operations() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableStrengthen.OPERATIONS : resolved.operations();
    }

    /** Bridge contract implemented by the EmakiStrengthen runtime. */
    @org.jetbrains.annotations.ApiStatus.NonExtendable
    public interface Bridge {

        /** {@return availability and identity metadata; must never be {@code null}} */
        @NotNull
        ApiStatus status();

        /**
         * {@return the read-only item mastery layer; defaults to the unavailable implementation for
         * bridges compiled before this layer existed}
         */
        default @NotNull ItemMastery mastery() {
            return UnavailableStrengthen.MASTERY;
        }

        /** {@return the read-only catalog layer; must never be {@code null}} */
        @NotNull
        StrengthenCatalog catalog();

        /** {@return the state-changing operation layer; must never be {@code null}} */
        @NotNull
        StrengthenOperations operations();
    }
}
