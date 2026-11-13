package emaki.jiuwu.craft.kits.api;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.corelib.api.contract.BridgeState;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for the EmakiKits kit, reward and CDK system.
 *
 * <p>Capabilities are grouped behind four accessors rather than flattened onto this class:
 * {@link #status()} for availability, {@link #catalog()} for read-only kit and play-time queries,
 * {@link #operations()} for claiming, purchasing and redeeming, and {@link #extensions()} for the
 * reserved extension layer.
 *
 * <p>Check {@code status().usable()} before relying on results. The accessors never return
 * {@code null}: while EmakiKits is absent they return no-op implementations whose queries yield
 * empty answers and whose operations yield
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}. Resolve accessors at
 * the point of use rather than caching them in a field, because the backing bridge is replaced
 * across a reload.
 *
 * <p>Depend on {@code emaki-kits-api} with {@code provided} (Maven) or {@code compileOnly}
 * (Gradle). EmakiKits' jar already carries an un-relocated copy of these classes; a second copy
 * would make your event listeners silently unreachable.
 */
public final class EmakiKitsApi {

    private static final BridgeState<Bridge> bridge = new BridgeState<>();

    private EmakiKitsApi() {
    }

    /**
     * Installs the backing bridge. Intended for the EmakiKits lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiKits
     */
    @Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiKitsApi.bridge.activate(bridge);
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    @Internal
    public static void uninstall(@Nullable Bridge bridge) {
        EmakiKitsApi.bridge.deactivate(bridge);
    }

    /**
     * {@return availability and identity metadata; never {@code null}, and
     * {@link ApiStatus#notInstalled()} when no bridge is installed}
     */
    public static @NotNull ApiStatus status() {
        Bridge resolved = bridge.get();
        return resolved == null ? ApiStatus.notInstalled() : resolved.status();
    }

    /**
     * {@return read-only kit and play-time queries; never {@code null}, and an empty-answer
     * implementation when EmakiKits is unavailable}
     */
    public static @NotNull KitCatalog catalog() {
        Bridge resolved = bridge.get();
        return resolved == null ? UnavailableKits.CATALOG : resolved.catalog();
    }

    /**
     * {@return claim, purchase and redemption operations; never {@code null}, and an implementation
     * that reports unavailability when EmakiKits is absent}
     */
    public static @NotNull KitOperations operations() {
        Bridge resolved = bridge.get();
        return resolved == null ? UnavailableKits.OPERATIONS : resolved.operations();
    }

    /**
     * {@return the reserved extension layer; never {@code null}}
     */
    public static @NotNull KitExtensions extensions() {
        Bridge resolved = bridge.get();
        return resolved == null ? UnavailableKits.EXTENSIONS : resolved.extensions();
    }

    /**
     * Bridge contract implemented by EmakiKits. Third-party plugins must not implement it.
     */
    @NonExtendable
    public interface Bridge {

        /** {@return availability and identity metadata; must never be {@code null}} */
        @NotNull
        ApiStatus status();

        /** {@return the read-only query layer; must never be {@code null}} */
        @NotNull
        KitCatalog catalog();

        /** {@return the write operation layer; must never be {@code null}} */
        @NotNull
        KitOperations operations();

        /** {@return the reserved extension layer; must never be {@code null}} */
        @NotNull
        KitExtensions extensions();
    }
}
