package emaki.jiuwu.craft.station.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for EmakiStation.
 *
 * <p>Use {@link #status()} to inspect availability, {@link #catalog()} for station, recipe, and queue
 * queries, and {@link #operations()} for submissions, cancellations, and claims. Accessors never
 * return {@code null}; while EmakiStation is absent the layers return explicit
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#UNAVAILABLE} results instead, so callers
 * must not treat a {@link NullPointerException} as an availability signal.
 */
public final class EmakiStationApi {

    private static volatile Bridge bridge;

    private EmakiStationApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiStation's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiStation
     */
    @ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiStationApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    @ApiStatus.Internal
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiStationApi.bridge == bridge) {
            EmakiStationApi.bridge = null;
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
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus status = resolved.status();
        return status == null
                ? emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled()
                : status;
    }

    /**
     * {@return station, recipe, and queue queries; never {@code null}, and a no-op implementation
     * reporting unavailability while no bridge is installed}
     */
    public static @NotNull StationCatalog catalog() {
        Bridge resolved = bridge;
        if (resolved == null) {
            return UnavailableStation.CATALOG;
        }
        StationCatalog catalog = resolved.catalog();
        return catalog == null ? UnavailableStation.CATALOG : catalog;
    }

    /**
     * {@return submission, cancellation, claim, and GUI operations; never {@code null}, and a no-op
     * implementation reporting unavailability while no bridge is installed}
     */
    public static @NotNull StationOperations operations() {
        Bridge resolved = bridge;
        if (resolved == null) {
            return UnavailableStation.OPERATIONS;
        }
        StationOperations operations = resolved.operations();
        return operations == null ? UnavailableStation.OPERATIONS : operations;
    }

    /**
     * {@return the reserved extension surface; never {@code null}, and a no-op implementation while no
     * bridge is installed}
     */
    public static @NotNull StationExtensions extensions() {
        Bridge resolved = bridge;
        if (resolved == null) {
            return UnavailableStation.EXTENSIONS;
        }
        StationExtensions extensions = resolved.extensions();
        return extensions == null ? UnavailableStation.EXTENSIONS : extensions;
    }

    /** Bridge contract implemented by EmakiStation. Third-party plugins must not implement it. */
    @ApiStatus.NonExtendable
    public interface Bridge {

        /** {@return availability and identity metadata; must never be {@code null}} */
        @NotNull
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();

        /** {@return the query layer; must never be {@code null}} */
        @NotNull
        StationCatalog catalog();

        /** {@return the operation layer; must never be {@code null}} */
        @NotNull
        StationOperations operations();

        /** {@return the reserved extension surface; must never be {@code null}} */
        @NotNull
        StationExtensions extensions();
    }
}
