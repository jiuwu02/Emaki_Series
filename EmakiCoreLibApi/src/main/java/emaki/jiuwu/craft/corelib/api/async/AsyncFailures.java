package emaki.jiuwu.craft.corelib.api.async;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Shared unwrapping for async failure causes.
 *
 * <p>Two contracts are intentionally kept separate because call sites depend on the
 * difference: {@link #unwrap(Throwable)} collapses an entire wrapper chain, while
 * {@link #unwrapOnce(Throwable)} peels exactly one {@link CompletionException} layer.
 * Swapping one for the other changes which throwable a caller reports.
 */
public final class AsyncFailures {

    private AsyncFailures() {
    }

    /**
     * Unwraps every consecutive {@link CompletionException} / {@link ExecutionException}
     * wrapper and returns the innermost cause.
     *
     * @return the original throwable when it is not a wrapper or has no cause
     */
    public static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * Peels exactly one {@link CompletionException} layer.
     *
     * @return the direct cause, or the original throwable when it is not a
     *         {@code CompletionException} or carries no cause
     */
    public static Throwable unwrapOnce(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }

    /**
     * Renders a compact {@code SimpleName: message} description of the innermost cause.
     */
    public static String describe(Throwable throwable) {
        if (throwable == null) {
            return "null";
        }
        Throwable root = unwrap(throwable);
        String message = root.getMessage();
        return root.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
