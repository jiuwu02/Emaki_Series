package emaki.jiuwu.craft.corelib.script.js;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionExecutionMode;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptRegisteredAction implements Action {

    private final Plugin plugin;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final String id;
    private final String category;
    private final String description;
    private final List<ActionParameter> parameters;
    private final ActionExecutionMode executionMode;
    private final long timeoutMillis;
    private final String scriptPath;
    private final String executeFunction;
    private final String validateFunction;
    private final boolean acceptsDynamicParameters;

    public JavaScriptRegisteredAction(Plugin plugin,
            JavaScriptService javaScriptService,
            ScriptConfig scriptConfig,
            String id,
            String category,
            String description,
            List<ActionParameter> parameters,
            ActionExecutionMode executionMode,
            long timeoutMillis,
            String scriptPath,
            String executeFunction,
            String validateFunction,
            boolean acceptsDynamicParameters) {
        this.plugin = plugin;
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
        this.id = Texts.normalizeId(id);
        this.category = Texts.isBlank(category) ? "javascript" : category;
        this.description = Texts.isBlank(description) ? this.id : description;
        this.parameters = parameters == null ? List.of() : List.copyOf(parameters);
        this.executionMode = executionMode == null ? ActionExecutionMode.SYNC : executionMode;
        this.timeoutMillis = timeoutMillis <= 0L ? this.scriptConfig.engine().defaultTimeoutMillis() : timeoutMillis;
        this.scriptPath = scriptPath;
        this.executeFunction = Texts.isBlank(executeFunction) ? "execute" : executeFunction;
        this.validateFunction = Texts.trim(validateFunction);
        this.acceptsDynamicParameters = acceptsDynamicParameters;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    public List<ActionParameter> parameters() {
        return parameters;
    }

    @Override
    public boolean acceptsDynamicParameter(String name) {
        return acceptsDynamicParameters;
    }

    @Override
    public ActionExecutionMode executionMode() {
        return executionMode;
    }

    @Override
    public long timeoutMillis() {
        return timeoutMillis;
    }

    @Override
    public ActionResult validate(Map<String, String> arguments) {
        return Action.super.validate(arguments);
    }

    @Override
    public CompletionStage<ActionResult> validateAsync(Map<String, String> arguments) {
        ActionResult base = Action.super.validate(arguments);
        if (!base.success() || Texts.isBlank(validateFunction)) {
            return CompletableFuture.completedFuture(base);
        }
        if (javaScriptService == null || !javaScriptService.enabled()) {
            return CompletableFuture.completedFuture(
                    ActionResult.failure(ActionErrorType.INVALID_STATE, "JavaScript scripting is unavailable."));
        }
        Map<String, String> safeArguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        ScriptInvocationRequest request = new ScriptInvocationRequest(
                plugin,
                null,
                scriptPath,
                validateFunction,
                List.of(safeArguments),
                Map.of("action_id", id),
                scriptConfig.clampTimeoutMillis(timeoutMillis),
                false
        );
        return javaScriptService.invokeAsync(request).thenApply(this::toActionResult);
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        return ActionResult.failure(ActionErrorType.UNSUPPORTED,
                "JavaScript actions must be executed asynchronously.");
    }

    @Override
    public CompletionStage<ActionResult> executeAsync(ActionContext context, Map<String, String> arguments) {
        if (javaScriptService == null || !javaScriptService.enabled()) {
            return CompletableFuture.completedFuture(
                    ActionResult.failure(ActionErrorType.INVALID_STATE, "JavaScript scripting is unavailable."));
        }
        Map<String, String> safeArguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        ScriptInvocationRequest request = new ScriptInvocationRequest(
                context == null ? plugin : context.sourcePlugin(),
                context,
                scriptPath,
                executeFunction,
                List.of(context == null ? Map.of() : context, safeArguments),
                Map.of("action_id", id),
                scriptConfig.clampTimeoutMillis(timeoutMillis),
                false
        );
        return javaScriptService.invokeAsync(request).thenApply(this::toActionResult);
    }

    private ActionResult toActionResult(ScriptExecutionResult result) {
        if (result == null) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "JavaScript action returned no result.");
        }
        if (result.skipped()) {
            return ActionResult.skipped(result.message());
        }
        if (result.success()) {
            java.util.LinkedHashMap<String, Object> data = new java.util.LinkedHashMap<>(result.output());
            if (result.returnValue() != null) {
                data.put("return", result.returnValue());
            }
            if (Texts.isNotBlank(result.message())) {
                data.put("message", result.message());
            }
            return ActionResult.ok(data);
        }
        return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION,
                Texts.isBlank(result.message()) ? "JavaScript action failed." : result.message());
    }
}
