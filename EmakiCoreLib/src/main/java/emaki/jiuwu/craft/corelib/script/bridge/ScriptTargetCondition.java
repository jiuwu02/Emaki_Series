package emaki.jiuwu.craft.corelib.script.bridge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetCondition;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetConditionArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetOutcome;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.script.host.JsConversions;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackException;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackRunner;

final class ScriptTargetCondition implements CoreTargetCondition {

    private final String id;
    private final String description;
    private final Value test;
    private final ScriptCallbackRunner runner;
    private final Consumer<String> warns;

    ScriptTargetCondition(@NotNull String id,
            @NotNull String description,
            @NotNull Value test,
            @NotNull ScriptCallbackRunner runner,
            @NotNull Consumer<String> warns) {
        this.id = id;
        this.description = description;
        this.test = test;
        this.runner = runner;
        this.warns = warns;
    }

    @Override
    public @NotNull String id() {
        return id;
    }

    @Override
    public @NotNull String description() {
        return description;
    }

    @Override
    public @NotNull CoreTargetOutcome test(@NotNull CoreActionSubject subject,
            @NotNull CoreStageContext context,
            @NotNull CoreTargetConditionArguments arguments) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("target", new ScriptSubjectExport(subject));
        payload.put("context", new ScriptStageContextExport(context));
        payload.put("arguments", JsConversions.deepToJs(arguments.raw()));
        try {
            return verdictOf(runner.run(test, payload));
        } catch (ScriptCallbackException exception) {
            warn(exception);
            return CoreTargetOutcome.FAIL;
        }
    }

    private static CoreTargetOutcome verdictOf(@Nullable Value result) {
        if (result == null || result.isNull()) {
            return CoreTargetOutcome.FAIL;
        }
        if (result.isBoolean()) {
            return result.asBoolean() ? CoreTargetOutcome.PASS : CoreTargetOutcome.FAIL;
        }
        if (result.isNumber()) {
            double value = result.asDouble();
            return !Double.isNaN(value) && value != 0.0D
                    ? CoreTargetOutcome.PASS
                    : CoreTargetOutcome.FAIL;
        }
        if (result.isString()) {
            return result.asString().isEmpty() ? CoreTargetOutcome.FAIL : CoreTargetOutcome.PASS;
        }
        return CoreTargetOutcome.PASS;
    }

    private void warn(@Nullable ScriptCallbackException exception) {
        String detail = exception == null || Texts.isBlank(exception.getMessage())
                ? "returned a non-boolean value"
                : exception.getMessage();
        warns.accept("target_conditions: condition '" + id + "' failed: " + Texts.toStringSafe(detail));
    }
}
