package emaki.jiuwu.craft.corelib.integration;

import java.lang.reflect.InvocationTargetException;

public final class IntegrationFailures {

    private IntegrationFailures() {
    }

    public static String detail(Throwable throwable) {
        Throwable resolved = throwable instanceof InvocationTargetException invocation
                && invocation.getCause() != null
                ? invocation.getCause()
                : throwable;
        String message = resolved.getMessage();
        return message == null || message.isBlank() ? resolved.getClass().getSimpleName() : message;
    }
}
