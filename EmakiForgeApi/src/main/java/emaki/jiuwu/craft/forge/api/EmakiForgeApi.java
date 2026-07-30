package emaki.jiuwu.craft.forge.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for the EmakiForge forging system.
 *
 * <h2>Layout</h2>
 * Capabilities are grouped behind three accessors rather than flattened onto this class:
 * {@link #catalog()} for read-only recipe, preview, and mastery queries, {@link #operations()} for
 * forging, GUI, and item-refresh actions, and {@link #extensions()} for third-party extension points.
 * {@link #status()} reports availability.
 *
 * <h2>Availability</h2>
 * Check {@code status().usable()} before relying on results. The accessors never return {@code null};
 * when EmakiForge is absent they return no-op implementations whose queries yield empty collections
 * and whose operations yield
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}.
 *
 * <h2>Do not shade</h2>
 * Depend on {@code emaki-forge-api} with {@code provided} (Maven) or {@code compileOnly} (Gradle).
 * EmakiForge's jar already carries an un-relocated copy of these classes; a second copy would make
 * your event listeners silently unreachable.
 */
public final class EmakiForgeApi {

    private static volatile Bridge bridge;

    private EmakiForgeApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiForge's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiForge
     */
    @ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiForgeApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    @ApiStatus.Internal
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiForgeApi.bridge == bridge) {
            EmakiForgeApi.bridge = null;
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
     * {@return read-only recipe and material queries; never {@code null}, and an empty-answer
     * implementation when EmakiForge is unavailable}
     */
    public static @NotNull ForgeCatalog catalog() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableForge.CATALOG : resolved.catalog();
    }

    /**
     * {@return forging, GUI, and item-refresh operations; never {@code null}, and an implementation
     * that reports unavailability when EmakiForge is absent}
     */
    public static @NotNull ForgeOperations operations() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableForge.OPERATIONS : resolved.operations();
    }

    /**
     * {@return Forge extension points; never {@code null}}
     */
    public static @NotNull ForgeExtensions extensions() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableForge.EXTENSIONS : resolved.extensions();
    }

    /**
     * Bridge contract implemented by EmakiForge. Third-party plugins must not implement it.
     */
    @ApiStatus.NonExtendable
    public interface Bridge {

        /** {@return availability and identity metadata; must never be {@code null}} */
        @NotNull
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();

        /** {@return the read-only query layer} */
        @NotNull
        ForgeCatalog catalog();

        /** {@return the write operation layer} */
        @NotNull
        ForgeOperations operations();

        /** {@return the extension layer} */
        @NotNull
        ForgeExtensions extensions();
    }
}
