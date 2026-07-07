package emaki.jiuwu.craft.skills.script.js;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.skills.api.SkillActionErrorType;
import emaki.jiuwu.craft.skills.api.SkillActionExecutionMode;
import emaki.jiuwu.craft.skills.api.SkillActionParameter;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.api.SkillScriptAction;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

public final class JavaScriptSkillAction implements SkillScriptAction {

    private final EmakiSkillsPlugin plugin;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final String id;
    private final String category;
    private final String description;
    private final List<SkillActionParameter> parameters;
    private final SkillActionExecutionMode executionMode;
    private final long timeoutMillis;
    private final String scriptPath;
    private final String executeFunction;
    private final String validateFunction;

    public JavaScriptSkillAction(EmakiSkillsPlugin plugin,
            JavaScriptService javaScriptService,
            ScriptConfig scriptConfig,
            String id,
            String category,
            String description,
            List<SkillActionParameter> parameters,
            SkillActionExecutionMode executionMode,
            long timeoutMillis,
            String scriptPath,
            String executeFunction,
            String validateFunction) {
        this.plugin = plugin;
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
        this.id = Texts.normalizeId(id);
        this.category = Texts.isBlank(category) ? "javascript" : category;
        this.description = Texts.isBlank(description) ? this.id : description;
        this.parameters = parameters == null ? List.of() : List.copyOf(parameters);
        this.executionMode = executionMode == null ? SkillActionExecutionMode.SYNC : executionMode;
        this.timeoutMillis = timeoutMillis <= 0L ? this.scriptConfig.engine().defaultTimeoutMillis() : timeoutMillis;
        this.scriptPath = scriptPath;
        this.executeFunction = Texts.isBlank(executeFunction) ? "execute" : executeFunction;
        this.validateFunction = Texts.trim(validateFunction);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public List<SkillActionParameter> parameters() {
        return parameters;
    }

    @Override
    public SkillActionExecutionMode executionMode() {
        return executionMode;
    }

    @Override
    public long timeoutMillis() {
        return timeoutMillis;
    }

    @Override
    public SkillActionResult validate(Map<String, String> arguments) {
        SkillActionResult base = SkillScriptAction.super.validate(arguments);
        if (!base.success() || Texts.isBlank(validateFunction)) {
            return base;
        }
        ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                plugin,
                null,
                scriptPath,
                validateFunction,
                List.of(arguments == null ? Map.of() : arguments),
                Map.of("action_id", id),
                scriptConfig.clampTimeoutMillis(timeoutMillis),
                false
        ));
        return toSkillActionResult(result);
    }

    @Override
    public CompletableFuture<SkillActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        if (javaScriptService == null || !javaScriptService.enabled()) {
            return CompletableFuture.completedFuture(SkillActionResult.failure(SkillActionErrorType.INVALID_STATE, "JavaScript scripting is unavailable."));
        }
        ScriptSkillContextApi scriptContext = new ScriptSkillContextApi(context);
        java.util.function.Supplier<SkillActionResult> task = () -> toSkillActionResult(javaScriptService.invoke(new ScriptInvocationRequest(
                plugin,
                null,
                scriptPath,
                executeFunction,
                List.of(scriptContext, arguments == null ? Map.of() : arguments),
                Map.of("action_id", id),
                scriptConfig.clampTimeoutMillis(timeoutMillis),
                false
        )));
        if (executionMode == SkillActionExecutionMode.ASYNC_IO) {
            CompletableFuture<SkillActionResult> future = new CompletableFuture<>();
            if (FoliaSchedulerAdapter.runAsync(plugin, () -> {
                try {
                    future.complete(task.get());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            }) == null) {
                future.completeExceptionally(new IllegalStateException("JavaScript skill action async scheduler is unavailable."));
            }
            return future;
        }
        return CompletableFuture.completedFuture(task.get());
    }

    private SkillActionResult toSkillActionResult(ScriptExecutionResult result) {
        if (result == null) {
            return SkillActionResult.failure(SkillActionErrorType.EXECUTION_EXCEPTION, "JavaScript skill action returned no result.");
        }
        if (result.skipped()) {
            return SkillActionResult.skipped(result.message());
        }
        if (result.success()) {
            java.util.LinkedHashMap<String, Object> data = new java.util.LinkedHashMap<>(result.output());
            if (result.returnValue() != null) {
                data.put("return", result.returnValue());
            }
            if (Texts.isNotBlank(result.message())) {
                data.put("message", result.message());
            }
            return SkillActionResult.ok(data);
        }
        return SkillActionResult.failure(SkillActionErrorType.EXECUTION_EXCEPTION,
                Texts.isBlank(result.message()) ? "JavaScript skill action failed." : result.message());
    }
}
