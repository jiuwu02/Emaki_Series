package emaki.jiuwu.craft.corelib.script.bridge;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackException;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackRunner;
import net.objecthunter.exp4j.function.Function;

final class ScriptExpressionFunction extends Function {

    private final Value fn;
    private final ScriptCallbackRunner runner;
    private final Consumer<String> warns;
    private final AtomicBoolean warned = new AtomicBoolean();

    ScriptExpressionFunction(@NotNull String name,
            int arguments,
            @NotNull Value fn,
            @NotNull ScriptCallbackRunner runner,
            @NotNull Consumer<String> warns) {
        super(name, arguments);
        this.fn = fn;
        this.runner = runner;
        this.warns = warns;
    }

    @Override
    public double apply(double... args) {
        Object[] boxed = new Object[args.length];
        for (int index = 0; index < args.length; index++) {
            boxed[index] = args[index];
        }
        try {
            Value result = runner.run(fn, boxed);
            if (result == null || result.isNull() || !result.isNumber()) {
                warnOnce(null);
                return Double.NaN;
            }
            try {
                return result.asDouble();
            } catch (RuntimeException exception) {
                warnOnce(null);
                return Double.NaN;
            }
        } catch (ScriptCallbackException exception) {
            warnOnce(exception);
            return Double.NaN;
        }
    }

    private void warnOnce(@org.jetbrains.annotations.Nullable ScriptCallbackException exception) {
        if (!warned.compareAndSet(false, true)) {
            return;
        }
        String detail = exception == null || Texts.isBlank(exception.getMessage())
                ? "returned a non-numeric value"
                : exception.getMessage();
        warns.accept("expressions: function '" + getName() + "' " + detail);
    }
}
