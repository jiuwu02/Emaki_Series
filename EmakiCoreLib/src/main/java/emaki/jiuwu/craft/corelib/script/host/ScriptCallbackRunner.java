package emaki.jiuwu.craft.corelib.script.host;

import java.util.concurrent.ScheduledFuture;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;

public final class ScriptCallbackRunner {

    private final ScriptHost host;

    ScriptCallbackRunner(@NotNull ScriptHost host) {
        this.host = host;
    }

    public @NotNull Value run(@NotNull Value function, Object... arguments) throws ScriptCallbackException {
        host.lockExclusive();
        try {
            Context context = host.currentContext();
            Object[] converted = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                Object argument = arguments[i];
                if (argument instanceof Value value) {
                    converted[i] = value;
                } else {
                    converted[i] = context.asValue(JsConversions.deepToJs(argument));
                }
            }
            ScheduledFuture<?> pendingInterrupt = host.scheduleInterrupt(context);
            try {
                return function.execute(converted);
            } catch (PolyglotException exception) {
                if (host.isTimeoutInterruption(exception)) {
                    throw ScriptCallbackException.timeout(exception);
                }
                if (exception.isInterrupted()) {
                    throw ScriptCallbackException.interrupted();
                }
                throw ScriptCallbackException.error(exception);
            } catch (RuntimeException exception) {
                if (Thread.currentThread().isInterrupted()) {
                    throw ScriptCallbackException.interrupted();
                }
                throw ScriptCallbackException.error(exception);
            } finally {
                pendingInterrupt.cancel(false);
            }
        } finally {
            host.unlockExclusive();
        }
    }
}
