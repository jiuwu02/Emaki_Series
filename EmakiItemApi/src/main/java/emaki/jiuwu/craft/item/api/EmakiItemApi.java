package emaki.jiuwu.craft.item.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;

/**
 * Static public facade for EmakiItem.
 *
 * <p>Accessors never return {@code null}. With no installed bridge, catalog queries degrade to documented
 * empty values, result-bearing calls return {@code UNAVAILABLE}, and extension registration returns an
 * inactive closeable handle. Depend on this artifact with {@code provided} or {@code compileOnly}; never
 * shade it into a third-party plugin because Bukkit event delivery uses class identity.
 */
public final class EmakiItemApi {

    private static volatile Bridge bridge;

    private EmakiItemApi() {
    }

    /** Installs the runtime bridge. Runtime use only. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiItemApi.bridge = bridge;
    }

    /** Removes the runtime bridge when it is still the active instance. Runtime use only. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiItemApi.bridge == bridge) {
            EmakiItemApi.bridge = null;
        }
    }

    /** {@return runtime availability and identity metadata} */
    public static @NotNull ApiStatus status() {
        Bridge resolved = bridge;
        return resolved == null ? ApiStatus.notInstalled() : resolved.status();
    }

    /** {@return the read-only catalog layer or its unavailable implementation} */
    public static @NotNull ItemCatalog catalog() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableItem.CATALOG : resolved.catalog();
    }

    /** {@return the item operation layer or its unavailable implementation} */
    public static @NotNull ItemOperations operations() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableItem.OPERATIONS : resolved.operations();
    }

    /** {@return the repair layer or its unavailable implementation} */
    public static @NotNull ItemRepair repair() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableItem.REPAIR : resolved.repair();
    }

    /** {@return the administrative migration layer or its unavailable implementation} */
    public static @NotNull ItemMigration migration() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableItem.MIGRATION : resolved.migration();
    }

    /** {@return the extension registration layer or its unavailable implementation} */
    public static @NotNull ItemExtensions extensions() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableItem.EXTENSIONS : resolved.extensions();
    }

    /** Runtime bridge contract. Third-party plugins must not implement it. */
    @org.jetbrains.annotations.ApiStatus.NonExtendable
    public interface Bridge {

        @NotNull ApiStatus status();

        @NotNull ItemCatalog catalog();

        @NotNull ItemOperations operations();

        @NotNull ItemRepair repair();

        @NotNull ItemMigration migration();

        @NotNull ItemExtensions extensions();
    }
}
