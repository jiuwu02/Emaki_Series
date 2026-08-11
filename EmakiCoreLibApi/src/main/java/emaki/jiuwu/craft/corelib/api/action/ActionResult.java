package emaki.jiuwu.craft.corelib.api.action;

import java.util.Map;

/**
 * Result of one economy operation.
 *
 * <p>Named after the removed v1 action executor, but now owned by {@code corelib.economy}:
 * {@code EconomyProvider} and {@code EconomyManager} declare their add/remove/set results in terms of it,
 * and six business modules branch on it when charging or refunding. It is therefore live infrastructure of
 * that subsystem rather than migration debt, and must not be retired while the economy layer is declared
 * in terms of it.</p>
 *
 * <p>Pipeline stages never return this type; {@code MoneyStage} converts it to {@code CoreActionOutcome}
 * at the boundary. The v1 {@code skipped} component is gone: no economy provider ever reported a skip, so
 * it was permanently {@code false}. A stage that genuinely has nothing to do reports
 * {@code CoreActionOutcome.Skipped} instead.</p>
 */
public record ActionResult(boolean success,
        ActionErrorType errorType,
        String errorMessage,
        Map<String, Object> data) {

    public static ActionResult ok() {
        return new ActionResult(true, ActionErrorType.NONE, null, Map.of());
    }

    public static ActionResult ok(Map<String, Object> data) {
        return new ActionResult(true, ActionErrorType.NONE, null, data == null ? Map.of() : Map.copyOf(data));
    }

    public static ActionResult failure(ActionErrorType errorType, String errorMessage) {
        return new ActionResult(false, errorType == null ? ActionErrorType.EXECUTION_EXCEPTION : errorType, errorMessage, Map.of());
    }
}
