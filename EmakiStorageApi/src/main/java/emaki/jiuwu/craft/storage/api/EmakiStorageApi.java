package emaki.jiuwu.craft.storage.api;

import emaki.jiuwu.craft.corelib.api.contract.BridgeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for the EmakiStorage warehouse.
 *
 * <p>Use {@link #status()} to inspect availability and {@link #operations()} for all storage queries,
 * mutations, and GUI opening. Accessors never return {@code null}; while EmakiStorage is absent the
 * operation layer returns explicit
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#UNAVAILABLE} results.
 */
public final class EmakiStorageApi {

    private static final BridgeState<Bridge> bridge = new BridgeState<>();

    private EmakiStorageApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiStorage's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiStorage
     */
    @ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiStorageApi.bridge.activate(bridge);
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    @ApiStatus.Internal
    public static void uninstall(@Nullable Bridge bridge) {
        EmakiStorageApi.bridge.deactivate(bridge);
    }

    /**
     * {@return availability and identity metadata; never {@code null}, and
     * {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#notInstalled()} when no bridge is installed}
     */
    public static @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status() {
        Bridge resolved = bridge.get();
        if (resolved == null) {
            return emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled();
        }
        var status = resolved.status();
        return status == null
                ? emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled()
                : status;
    }

    /**
     * {@return storage queries, mutations, and GUI operations; never {@code null}, and a no-op
     * implementation reporting unavailability while no bridge is installed}
     */
    public static @NotNull StorageOperations operations() {
        Bridge resolved = bridge.get();
        if (resolved == null) {
            return UnavailableStorage.OPERATIONS;
        }
        StorageOperations operations = resolved.operations();
        return operations == null ? UnavailableStorage.OPERATIONS : operations;
    }

    /** Bridge contract implemented by EmakiStorage. Third-party plugins must not implement it. */
    @ApiStatus.NonExtendable
    public interface Bridge {

        /** {@return availability and identity metadata; must never be {@code null}} */
        @NotNull
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();

        /** {@return the storage operation layer; must never be {@code null}} */
        @NotNull
        StorageOperations operations();
    }
}
