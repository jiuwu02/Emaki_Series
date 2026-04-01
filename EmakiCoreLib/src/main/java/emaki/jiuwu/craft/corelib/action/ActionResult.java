package emaki.jiuwu.craft.corelib.action;

import java.util.Map;

public record ActionResult(boolean success,
        boolean skipped,
        ActionErrorType errorType,
        String errorMessage,
        Map<String, Object> data) {

    public static ActionResult ok() {
        return new ActionResult(true, false, ActionErrorType.NONE, null, Map.of());
    }

    public static ActionResult ok(Map<String, Object> data) {
        return new ActionResult(true, false, ActionErrorType.NONE, null, data == null ? Map.of() : Map.copyOf(data));
    }

    public static ActionResult skipped(String reason) {
        return new ActionResult(true, true, ActionErrorType.NONE, reason, Map.of());
    }

    public static ActionResult failure(ActionErrorType errorType, String errorMessage) {
        return new ActionResult(false, false, errorType == null ? ActionErrorType.EXECUTION_EXCEPTION : errorType, errorMessage, Map.of());
    }
}
