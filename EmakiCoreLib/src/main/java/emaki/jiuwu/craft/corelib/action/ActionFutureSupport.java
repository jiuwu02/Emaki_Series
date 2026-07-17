package emaki.jiuwu.craft.corelib.action;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import emaki.jiuwu.craft.corelib.text.Texts;

final class ActionFutureSupport {

    private static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    private ActionFutureSupport() {
    }

    static CompletableFuture<ActionResult> withTimeout(ActionContext context,
            String actionId,
            CompletableFuture<ActionResult> future) {
        return withTimeout(context, actionId, DEFAULT_TIMEOUT_MILLIS, future);
    }

    static CompletableFuture<ActionResult> withTimeout(ActionContext context,
            String actionId,
            long configuredTimeoutMillis,
            CompletableFuture<ActionResult> future) {
        if (future == null) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    ActionErrorType.EXECUTION_EXCEPTION,
                    "Action '" + Texts.toStringSafe(actionId) + "' returned no future."));
        }
        long timeoutMillis = resolveTimeoutMillis(context, configuredTimeoutMillis);
        CompletableFuture<ActionResult> result = new CompletableFuture<>();
        future.whenComplete((value, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
            } else {
                result.complete(value == null ? ActionResult.ok() : value);
            }
        });
        CompletableFuture.delayedExecutor(timeoutMillis, TimeUnit.MILLISECONDS).execute(() -> {
            ActionResult timeout = ActionResult.failure(
                    ActionErrorType.EXECUTION_EXCEPTION,
                    "Action '" + Texts.toStringSafe(actionId) + "' timed out after " + timeoutMillis + " ms.");
            if (result.complete(timeout)) {
                future.cancel(true);
            }
        });
        return result;
    }

    private static long resolveTimeoutMillis(ActionContext context, long configuredTimeoutMillis) {
        if (context != null) {
            long timeoutMillis = parsePositiveLong(context.attribute("action_timeout_millis"));
            if (timeoutMillis > 0L) {
                return timeoutMillis;
            }
            long timeoutSeconds = parsePositiveLong(context.attribute("action_timeout_seconds"));
            if (timeoutSeconds > 0L) {
                return Math.multiplyExact(timeoutSeconds, 1_000L);
            }
        }
        return configuredTimeoutMillis > 0L ? configuredTimeoutMillis : DEFAULT_TIMEOUT_MILLIS;
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
        } catch (NumberFormatException _) {
            return -1L;
        }
    }
}
