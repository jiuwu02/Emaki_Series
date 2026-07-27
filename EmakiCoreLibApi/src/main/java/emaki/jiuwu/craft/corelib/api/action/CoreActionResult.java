package emaki.jiuwu.craft.corelib.api.action;

import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Result returned by an action implementation.
 */
public record CoreActionResult(
        boolean success,
        boolean skipped,
        @NotNull CoreActionErrorType errorType,
        @Nullable String errorMessage,
        @NotNull Map<String, Object> data) {

    public CoreActionResult {
        errorType = errorType == null ? CoreActionErrorType.NONE : errorType;
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static @NotNull CoreActionResult ok() {
        return new CoreActionResult(true, false, CoreActionErrorType.NONE, null, Map.of());
    }

    public static @NotNull CoreActionResult ok(@Nullable Map<String, Object> data) {
        return new CoreActionResult(true, false, CoreActionErrorType.NONE, null, data);
    }

    public static @NotNull CoreActionResult skipped(@Nullable String reason) {
        return new CoreActionResult(true, true, CoreActionErrorType.NONE, reason, Map.of());
    }

    public static @NotNull CoreActionResult failure(@Nullable CoreActionErrorType errorType, @Nullable String errorMessage) {
        return new CoreActionResult(false, false,
                errorType == null ? CoreActionErrorType.EXECUTION_EXCEPTION : errorType,
                errorMessage,
                Map.of());
    }
}
