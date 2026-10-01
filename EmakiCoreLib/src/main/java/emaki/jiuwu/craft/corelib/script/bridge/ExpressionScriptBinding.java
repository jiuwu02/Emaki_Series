package emaki.jiuwu.craft.corelib.script.bridge;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackRunner;

public final class ExpressionScriptBinding {

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]+");

    private final Supplier<ScriptCallbackRunner> runner;
    private final Consumer<String> errors;
    private final Consumer<String> warns;
    private final List<String> nameSink;

    ExpressionScriptBinding(@NotNull Supplier<ScriptCallbackRunner> runner,
            @NotNull Consumer<String> errors,
            @NotNull Consumer<String> warns,
            @NotNull List<String> nameSink) {
        this.runner = runner;
        this.errors = errors;
        this.warns = warns;
        this.nameSink = nameSink;
    }

    public void register(@Nullable Value payload) {
        if (payload == null || payload.isNull() || !payload.hasMembers()) {
            errors.accept("expressions: payload must be an object");
            return;
        }
        String name = Texts.toStringSafe(ScriptPayloads.textMember(payload, "name")).trim();
        if (Texts.isBlank(name)) {
            errors.accept("expressions: field 'name' is required");
            return;
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            errors.accept("expressions: function name '" + name + "' is invalid");
            return;
        }
        if (ExpressionEngine.isBuiltinFunctionName(name)) {
            errors.accept("expressions: function name '" + name + "' conflicts with built-in functions");
            return;
        }
        Integer arguments = ScriptPayloads.intMember(payload, "args");
        if (arguments == null || arguments < 1) {
            errors.accept("expressions: field 'args' of '" + name + "' must be an integer >= 1");
            return;
        }
        Value fn = ScriptPayloads.functionMember(payload, "fn");
        if (fn == null) {
            errors.accept("expressions: field 'fn' of '" + name + "' must be a callable function");
            return;
        }
        ScriptCallbackRunner resolvedRunner = runner.get();
        if (resolvedRunner == null) {
            errors.accept("expressions: '" + name + "' -> script host is unavailable");
            return;
        }
        if (!ExpressionEngine.registerDynamicFunction(name, new ScriptExpressionFunction(name, arguments, fn,
                resolvedRunner, warns))) {
            errors.accept("expressions: function name '" + name + "' is already registered");
            return;
        }
        nameSink.add(name);
    }
}
