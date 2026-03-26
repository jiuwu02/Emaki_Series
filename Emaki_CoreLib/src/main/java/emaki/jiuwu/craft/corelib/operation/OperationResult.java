package emaki.jiuwu.craft.corelib.operation;

import java.util.Map;

public record OperationResult(boolean success,
                              boolean skipped,
                              OperationErrorType errorType,
                              String errorMessage,
                              Map<String, Object> data) {

    public static OperationResult ok() {
        return new OperationResult(true, false, OperationErrorType.NONE, null, Map.of());
    }

    public static OperationResult ok(Map<String, Object> data) {
        return new OperationResult(true, false, OperationErrorType.NONE, null, data == null ? Map.of() : Map.copyOf(data));
    }

    public static OperationResult skipped(String reason) {
        return new OperationResult(true, true, OperationErrorType.NONE, reason, Map.of());
    }

    public static OperationResult failure(OperationErrorType errorType, String errorMessage) {
        return new OperationResult(false, false, errorType == null ? OperationErrorType.EXECUTION_EXCEPTION : errorType, errorMessage, Map.of());
    }
}
