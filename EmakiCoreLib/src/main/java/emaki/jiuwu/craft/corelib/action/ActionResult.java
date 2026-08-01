package emaki.jiuwu.craft.corelib.action;

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
 * at the boundary. {@link #skipped(String)} has no caller left — no economy provider reports a skip — so
 * the {@code skipped} component is currently always {@code false}.</p>
 */
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
