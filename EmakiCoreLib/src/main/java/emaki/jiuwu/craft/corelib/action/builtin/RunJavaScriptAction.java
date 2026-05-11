package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionExecutionMode;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionRequest;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class RunJavaScriptAction extends BaseAction {

    private static final String ARG_PREFIX = "arg_";

    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final String actionId;

    public RunJavaScriptAction(String actionId, JavaScriptService javaScriptService, ScriptConfig scriptConfig) {
        super(Texts.isBlank(actionId) ? "runjs" : Texts.normalizeId(actionId),
                "script",
                "Run a JavaScript script from the CoreLib script repository.",
                ActionParameter.required("script", ActionParameterType.STRING, "Script path relative to the script root"),
                ActionParameter.optional("function", ActionParameterType.STRING,
                        scriptConfig == null ? "main" : scriptConfig.action().defaultFunction(), "Function name to invoke"),
                ActionParameter.optional("timeout", ActionParameterType.STRING,
                        scriptConfig == null ? "1000" : Long.toString(scriptConfig.engine().defaultTimeoutMillis()), "Timeout in milliseconds"),
                ActionParameter.optional("silent", ActionParameterType.BOOLEAN, "false", "Suppress script error logging"));
        this.actionId = Texts.isBlank(actionId) ? "runjs" : Texts.normalizeId(actionId);
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
    }

    @Override
    public ActionExecutionMode executionMode() {
        return ActionExecutionMode.ASYNC_IO;
    }

    @Override
    public long timeoutMillis() {
        return scriptConfig.engine().maxTimeoutMillis();
    }

    @Override
    public boolean acceptsDynamicParameter(String name) {
        return Texts.isNotBlank(name) && Texts.lower(name).startsWith(ARG_PREFIX);
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        if (javaScriptService == null || !javaScriptService.enabled()) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "JavaScript scripting is unavailable.");
        }
        Map<String, String> resolved = applyDefaults(arguments);
        String script = stringArg(resolved, "script");
        String function = stringArg(resolved, "function");
        boolean silent = Boolean.parseBoolean(stringArg(resolved, "silent"));
        long timeout = scriptConfig.clampTimeoutMillis(ScriptConfig.parseMillis(stringArg(resolved, "timeout"), scriptConfig.engine().defaultTimeoutMillis()));
        Map<String, Object> scriptArguments = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : resolved.entrySet()) {
            if (Texts.lower(entry.getKey()).startsWith(ARG_PREFIX)) {
                scriptArguments.put(entry.getKey().substring(ARG_PREFIX.length()), entry.getValue());
            }
        }
        ScriptExecutionResult result = javaScriptService.executeJavaScript(new ScriptExecutionRequest(
                context == null ? null : context.sourcePlugin(),
                context,
                script,
                function,
                scriptArguments,
                timeout,
                silent
        ));
        if (result == null) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Script returned no result.");
        }
        if (result.skipped()) {
            return ActionResult.skipped(result.message());
        }
        if (result.success()) {
            Map<String, Object> data = new LinkedHashMap<>(result.output());
            if (result.returnValue() != null) {
                data.put("return", result.returnValue());
            }
            if (Texts.isNotBlank(result.message())) {
                data.put("message", result.message());
            }
            return ActionResult.ok(data);
        }
        return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION,
                Texts.isBlank(result.message()) ? "Script execution failed." : result.message());
    }

    public static List<RunJavaScriptAction> createAll(JavaScriptService service, ScriptConfig config) {
        ScriptConfig safeConfig = config == null ? ScriptConfig.defaults() : config;
        java.util.ArrayList<RunJavaScriptAction> actions = new java.util.ArrayList<>();
        actions.add(new RunJavaScriptAction(safeConfig.action().id(), service, safeConfig));
        for (String alias : safeConfig.action().aliases()) {
            if (Texts.isNotBlank(alias) && !Texts.normalizeId(alias).equals(safeConfig.action().id())) {
                actions.add(new RunJavaScriptAction(alias, service, safeConfig));
            }
        }
        return List.copyOf(actions);
    }
}
