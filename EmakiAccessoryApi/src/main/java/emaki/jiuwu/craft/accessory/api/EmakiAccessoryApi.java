package emaki.jiuwu.craft.accessory.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for EmakiAccessory.
 *
 * <p>Use {@link #status()} to inspect availability and {@link #catalog()} for part, slot and equipped
 * queries. Accessors never return {@code null}; while EmakiAccessory is absent the catalog returns
 * empty answers instead, so callers must not treat a {@link NullPointerException} as an availability
 * signal.
 *
 * <p>Accessories grant attributes and skills by pushing contributions into EmakiAttribute and
 * EmakiSkills through their existing owner-scoped extension points, so this module deliberately
 * exposes no operation or extension layer: there is no accessory-specific combat or skill pipeline for
 * a third party to hook. Resolve the layer at the point of use rather than caching it in a field,
 * because the backing bridge is replaced across a reload.
 */
public final class EmakiAccessoryApi {

    private static volatile Bridge bridge;

    private EmakiAccessoryApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiAccessory's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiAccessory
     */
    @ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiAccessoryApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    @ApiStatus.Internal
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiAccessoryApi.bridge == bridge) {
            EmakiAccessoryApi.bridge = null;
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
     * {@return part, slot and equipped queries; never {@code null}, and an empty-answer implementation
     * while no bridge is installed}
     */
    public static @NotNull AccessoryCatalog catalog() {
        Bridge resolved = bridge;
        if (resolved == null) {
            return UnavailableAccessory.CATALOG;
        }
        AccessoryCatalog catalog = resolved.catalog();
        return catalog == null ? UnavailableAccessory.CATALOG : catalog;
    }

    /** Bridge contract implemented by EmakiAccessory. Third-party plugins must not implement it. */
    @ApiStatus.NonExtendable
    public interface Bridge {

        /** {@return availability and identity metadata; must never be {@code null}} */
        @NotNull
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();

        /** {@return the query layer; must never be {@code null}} */
        @NotNull
        AccessoryCatalog catalog();
    }
}
