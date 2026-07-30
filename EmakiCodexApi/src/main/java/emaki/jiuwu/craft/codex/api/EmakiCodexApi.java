package emaki.jiuwu.craft.codex.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Static public facade for EmakiCodex. Accessors never return {@code null}. */
public final class EmakiCodexApi {

    private static volatile Bridge bridge;

    private EmakiCodexApi() { }

    @ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiCodexApi.bridge = bridge;
    }

    @ApiStatus.Internal
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiCodexApi.bridge == bridge) {
            EmakiCodexApi.bridge = null;
        }
    }

    public static @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status() {
        Bridge resolved = bridge;
        return resolved == null
                ? emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled()
                : resolved.status();
    }

    public static @NotNull CodexCatalog catalog() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableCodex.CATALOG : resolved.catalog();
    }

    public static @NotNull CodexOperations operations() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableCodex.OPERATIONS : resolved.operations();
    }

    public static @NotNull CodexExtensions extensions() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableCodex.EXTENSIONS : resolved.extensions();
    }

    /** Bridge contract implemented only by EmakiCodex. */
    @ApiStatus.NonExtendable
    public interface Bridge {
        @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();
        @NotNull CodexCatalog catalog();
        @NotNull CodexOperations operations();
        @NotNull CodexExtensions extensions();
    }
}
