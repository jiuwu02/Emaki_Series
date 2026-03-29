package emaki.jiuwu.craft.corelib.action;

import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class ActionFutureSupport {

    private static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    private ActionFutureSupport() {
    }

    static CompletableFuture<ActionResult> withTimeout(ActionContext context,
                                                       String actionId,
                                                       CompletableFuture<ActionResult> future) {
        long timeoutMillis = resolveTimeoutMillis(context);
        return future.completeOnTimeout(
            ActionResult.failure(
                ActionErrorType.EXECUTION_EXCEPTION,
                "Action '" + Texts.toStringSafe(actionId) + "' timed out after " + timeoutMillis + " ms."
            ),
            timeoutMillis,
            TimeUnit.MILLISECONDS
        );
    }

    private static long resolveTimeoutMillis(ActionContext context) {
        if (context == null) {
            return DEFAULT_TIMEOUT_MILLIS;
        }
        long timeoutMillis = parsePositiveLong(context.attribute("action_timeout_millis"));
        if (timeoutMillis > 0L) {
            return timeoutMillis;
        }
        long timeoutSeconds = parsePositiveLong(context.attribute("action_timeout_seconds"));
        if (timeoutSeconds > 0L) {
            return timeoutSeconds * 1_000L;
        }
        return DEFAULT_TIMEOUT_MILLIS;
    }

    private static long parsePositiveLong(Object raw) {
        if (raw instanceof Number number) {
            long value = number.longValue();
            return value > 0L ? value : -1L;
        }
        String text = Texts.toStringSafe(raw).trim();
        if (text.isEmpty()) {
            return -1L;
        }
        try {
            long value = Long.parseLong(text);
            return value > 0L ? value : -1L;
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }
}
