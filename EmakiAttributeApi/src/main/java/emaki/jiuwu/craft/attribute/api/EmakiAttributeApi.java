package emaki.jiuwu.craft.attribute.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.attribute.api.extension.AttributeExtensions;

/**
 * Static public facade for EmakiAttribute.
 *
 * <p>Use {@link #catalog()} for read-only queries, {@link #operations()} for resource, damage and
 * synchronization writes, and {@link #extensions()} for contribution providers, item gates and PDC.
 * Every accessor is non-null and degrades to an unavailable implementation when the runtime bridge is
 * absent.
 */
public final class EmakiAttributeApi {

    private static volatile Bridge bridge;

    private EmakiAttributeApi() {
    }

    /**
     * Installs the runtime bridge. Intended for EmakiAttribute lifecycle code only.
     *
     * @param bridge the runtime implementation to publish
     */
    @ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiAttributeApi.bridge = bridge;
    }

    /**
     * Removes the bridge only when it is still the active instance, so a stale instance from a previous
     * reload cannot uninstall its replacement.
     *
     * @param bridge the instance attempting to uninstall; a non-matching or {@code null} value is ignored
     */
    @ApiStatus.Internal
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiAttributeApi.bridge == bridge) {
            EmakiAttributeApi.bridge = null;
        }
    }

    /** {@return availability and identity metadata} */
    public static @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status() {
        Bridge resolved = bridge;
        return resolved == null
                ? emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled()
                : resolved.status();
    }

    /** {@return the read-only query layer; never {@code null}} */
    public static @NotNull AttributeCatalog catalog() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableAttribute.CATALOG : resolved.catalog();
    }

    /** {@return the state-changing operation layer; never {@code null}} */
    public static @NotNull AttributeOperations operations() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableAttribute.OPERATIONS : resolved.operations();
    }

    /** {@return the extension registration and PDC layer; never {@code null}} */
    public static @NotNull AttributeExtensions extensions() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableAttribute.EXTENSIONS : resolved.extensions();
    }

    /** Runtime contract implemented by EmakiAttribute; third-party plugins must not implement it. */
    @ApiStatus.NonExtendable
    public interface Bridge {

        /** {@return availability and identity metadata for the running EmakiAttribute instance} */
        @NotNull
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();

        /** {@return the runtime read-only query layer} */
        @NotNull
        AttributeCatalog catalog();

        /** {@return the runtime state-changing operation layer} */
        @NotNull
        AttributeOperations operations();

        /** {@return the runtime extension registration and PDC layer} */
        @NotNull
        AttributeExtensions extensions();
    }
}
