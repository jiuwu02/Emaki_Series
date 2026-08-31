package emaki.jiuwu.craft.cooking.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public facade for EmakiCooking recipes, stations and nutrition.
 *
 * <p>Accessors never return {@code null}: while the bridge is absent, queries are empty and result-bearing
 * calls are unavailable. Runtime readiness does not imply nutrition is enabled; check
 * {@link CookingNutrition#enabled()} separately.
 *
 * <p>Depend on this API as {@code provided} or {@code compileOnly}; do not shade it, because bridge and event
 * delivery require a single class identity.
 */
public final class EmakiCookingApi {

    private static volatile Bridge bridge;

    private EmakiCookingApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiCooking's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiCooking
     */
    @ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiCookingApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    @ApiStatus.Internal
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiCookingApi.bridge == bridge) {
            EmakiCookingApi.bridge = null;
        }
    }

    /**
     * {@return availability and identity metadata; never {@code null}, and
     * {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#notInstalled()} when no bridge is
     * installed}
     */
    public static @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status() {
        Bridge resolved = bridge;
        return resolved == null
                ? emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled()
                : resolved.status();
    }

    /**
     * {@return the player nutrition subsystem; never {@code null}, and an implementation reporting
     * unavailability when EmakiCooking is absent}
     */
    public static @NotNull CookingNutrition nutrition() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableCooking.NUTRITION : resolved.nutrition();
    }

    /**
     * {@return read-only recipe and station queries; never {@code null}, and an empty-answer
     * implementation when EmakiCooking is unavailable}
     */
    public static @NotNull CookingCatalog catalog() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableCooking.CATALOG : resolved.catalog();
    }

    /**
     * {@return state-changing operations; never {@code null}, and an implementation that reports
     * unavailability when EmakiCooking is absent}
     */
    public static @NotNull CookingOperations operations() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableCooking.OPERATIONS : resolved.operations();
    }

    /**
     * Bridge contract implemented by EmakiCooking. Third-party plugins must not implement it.
     */
    @ApiStatus.NonExtendable
    public interface Bridge {

        /** {@return availability and identity metadata; must never be {@code null}} */
        @NotNull
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();

        /** {@return the nutrition subsystem} */
        @NotNull
        CookingNutrition nutrition();

        /** {@return the read-only query layer} */
        @NotNull
        CookingCatalog catalog();

        /** {@return the write operation layer} */
        @NotNull
        CookingOperations operations();
    }
}
