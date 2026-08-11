package emaki.jiuwu.craft.codex.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public facade for EmakiCodex. Accessors never return {@code null}.
 *
 * <p>With no installed bridge every accessor hands back a stable no-op layer: catalog queries return empty
 * collections and empty optionals, result-bearing calls return {@code UNAVAILABLE}, and extension
 * registration returns an inactive closeable handle. Never test availability by catching
 * {@link NullPointerException}; call {@link #status()} instead.
 *
 * <p>Depend on this artifact with {@code provided} or {@code compileOnly} and do not shade it, because
 * Bukkit event delivery and bridge installation both rely on class identity.
 */
public final class EmakiCodexApi {

    private static volatile Bridge bridge;

    private EmakiCodexApi() { }

    /**
     * Installs the runtime bridge. Intended for EmakiCodex's own lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiCodex
     */
    @ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiCodexApi.bridge = bridge;
    }

    /**
     * Removes the runtime bridge when it is still the active instance. Runtime use only.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge, so a stale instance
     *               from a previous reload cannot uninstall the current one
     */
    @ApiStatus.Internal
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiCodexApi.bridge == bridge) {
            EmakiCodexApi.bridge = null;
        }
    }

    /**
     * {@return runtime availability and identity metadata; never {@code null}, and
     * {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#notInstalled()} when no bridge is installed}
     */
    public static @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status() {
        Bridge resolved = bridge;
        return resolved == null
                ? emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled()
                : resolved.status();
    }

    /**
     * {@return the read-only advancement and page catalog; a no-op layer answering with empty collections
     * and empty optionals when EmakiCodex is unavailable}
     */
    public static @NotNull CodexCatalog catalog() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableCodex.CATALOG : resolved.catalog();
    }

    /**
     * {@return the advancement mutation layer; a no-op layer whose calls all return {@code UNAVAILABLE}
     * when EmakiCodex is unavailable}
     */
    public static @NotNull CodexOperations operations() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableCodex.OPERATIONS : resolved.operations();
    }

    /**
     * {@return the owner-scoped extension registration layer; a no-op layer returning inactive handles
     * when EmakiCodex is unavailable}
     */
    public static @NotNull CodexExtensions extensions() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableCodex.EXTENSIONS : resolved.extensions();
    }

    /**
     * Bridge contract implemented only by EmakiCodex.
     *
     * <p>Every accessor must return a usable layer rather than {@code null}: the facade only substitutes
     * its own no-op layers when no bridge is installed at all, so a bridge returning {@code null} would
     * break the never-{@code null} contract the accessors above are documented under.
     */
    @ApiStatus.NonExtendable
    public interface Bridge {

        /** {@return runtime availability and identity metadata} */
        @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();

        /** {@return the runtime read-only catalog layer} */
        @NotNull CodexCatalog catalog();

        /** {@return the runtime advancement mutation layer} */
        @NotNull CodexOperations operations();

        /** {@return the runtime extension registration layer} */
        @NotNull CodexExtensions extensions();
    }
}
