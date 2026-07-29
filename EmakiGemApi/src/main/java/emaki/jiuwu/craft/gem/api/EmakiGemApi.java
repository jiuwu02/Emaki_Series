package emaki.jiuwu.craft.gem.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for the EmakiGem socket and gem system.
 *
 * <h2>Layout</h2>
 * {@link #catalog()} for read-only definition and socket-state queries, {@link #operations()} for
 * inlay, extraction, socket opening, and GUI actions. {@link #status()} reports availability.
 *
 * <h2>Availability</h2>
 * Check {@code status().usable()} before relying on results. The accessors never return {@code null};
 * when EmakiGem is absent they return no-op implementations whose queries yield empty answers and whose
 * operations yield {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}.
 *
 * <h2>Do not shade</h2>
 * Depend on {@code emaki-gem-api} with {@code provided} (Maven) or {@code compileOnly} (Gradle).
 * EmakiGem's jar already carries an un-relocated copy of these classes; a second copy would make your
 * event listeners silently unreachable.
 */
public final class EmakiGemApi {

    private static volatile Bridge bridge;

    private EmakiGemApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiGem's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiGem
     */
    @ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiGemApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    @ApiStatus.Internal
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiGemApi.bridge == bridge) {
            EmakiGemApi.bridge = null;
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
     * {@return read-only gem and socket queries; never {@code null}, and an empty-answer implementation
     * when EmakiGem is unavailable}
     */
    public static @NotNull GemCatalog catalog() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableGem.CATALOG : resolved.catalog();
    }

    /**
     * {@return inlay, extraction, socket, and GUI operations; never {@code null}, and an implementation
     * that reports unavailability when EmakiGem is absent}
     */
    public static @NotNull GemOperations operations() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableGem.OPERATIONS : resolved.operations();
    }

    /**
     * Bridge contract implemented by EmakiGem. Third-party plugins must not implement it.
     */
    @ApiStatus.NonExtendable
    public interface Bridge {

        /** {@return availability and identity metadata; must never be {@code null}} */
        @NotNull
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();

        /** {@return the read-only query layer} */
        @NotNull
        GemCatalog catalog();

        /** {@return the write operation layer} */
        @NotNull
        GemOperations operations();
    }
}
