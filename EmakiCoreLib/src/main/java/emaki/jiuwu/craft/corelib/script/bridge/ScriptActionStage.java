package emaki.jiuwu.craft.corelib.script.bridge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.script.host.JsConversions;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackException;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackRunner;

final class ScriptActionStage implements CoreActionStage {

    private final String id;
    private final String description;
    private final String category;
    private final List<CoreStageParameter> parameters;
    private final long timeoutMillis;
    private final Value execute;
    private final ScriptCallbackRunner runner;

    ScriptActionStage(@NotNull String id,
            @NotNull String description,
            @NotNull String category,
            @NotNull List<CoreStageParameter> parameters,
            long timeoutMillis,
            @NotNull Value execute,
            @NotNull ScriptCallbackRunner runner) {
        this.id = id;
        this.description = description;
        this.category = category;
        this.parameters = List.copyOf(parameters);
        this.timeoutMillis = timeoutMillis;
        this.execute = execute;
        this.runner = runner;
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
    public @NotNull String category() {
        return category;
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return parameters;
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.NONE;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.asyncCompute();
    }

    @Override
    public long timeoutMillis() {
        return timeoutMillis;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        ScriptStageContextExport contextExport = new ScriptStageContextExport(context);
        Map<String, String> raw = new LinkedHashMap<>(arguments.raw());
        Object argsExport = JsConversions.deepToJs(raw);
        try {
            Value result = runner.run(execute, contextExport, argsExport);
            return CoreActionOutcome.success(Map.of("script_result", describeResult(result)));
        } catch (ScriptCallbackException exception) {
            if (exception.isTimeout()) {
                return CoreActionOutcome.skipped("action.script.eval.timeout");
            }
            if (exception.isInterrupted()) {
                return CoreActionOutcome.skipped("action.script.eval.interrupted");
            }
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.script.eval.error",
                    Map.of("error_message", Texts.toStringSafe(exception.getMessage())));
        }
    }

    private static String describeResult(Value result) {
        if (result == null || result.isNull()) {
            return "null";
        }
        return Texts.toStringSafe(result.toString());
    }
}
