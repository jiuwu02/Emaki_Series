package emaki.jiuwu.craft.corelib.api.script;

import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptDeferredOperationQueue;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptActionApi {

    private static final String DEPTH_KEY = "emaki.script.actionDepth";

    private final ScriptDeferredOperationQueue deferredOperations;
    private final ScriptConfig.Security security;

    public ScriptActionApi(ActionExecutor actionExecutor, ActionContext context, ScriptConfig.Security security) {
        this(null, security);
    }

    public ScriptActionApi(ActionExecutor actionExecutor,
            ActionContext context,
            ExecutionDispatcher executionDispatcher,
            ScriptConfig.Security security) {
        this(executionDispatcher == null ? null : new ScriptDeferredOperationQueue(
                context == null ? null : context.sourcePlugin(),
                executionDispatcher,
                actionExecutor,
                context
        ), security);
    }

    public ScriptActionApi(ScriptDeferredOperationQueue deferredOperations, ScriptConfig.Security security) {
        this.deferredOperations = deferredOperations;
        this.security = security == null ? ScriptConfig.Security.defaults() : security;
    }

    @HostAccess.Export
    public boolean run(String actionId, Map<String, ?> arguments) {
        if (!canRun(actionId)) {
            return false;
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        if (arguments != null) {
            for (Map.Entry<String, ?> entry : arguments.entrySet()) {
                resolved.put(entry.getKey(), Texts.toStringSafe(entry.getValue()));
            }
        }
        return deferredOperations.enqueueAction(
                actionId,
                resolved,
                DEPTH_KEY,
                security.maxActionDepth()
        );
    }

    @HostAccess.Export
    public boolean runLine(String line) {
        if (Texts.isBlank(line) || !security.allowActionDispatch()) {
            return false;
        }
        String actionId = firstToken(line);
        if (!canRun(actionId)) {
            return false;
        }
        return deferredOperations.enqueueActionLine(
                line,
                DEPTH_KEY,
                security.maxActionDepth()
        );
    }

    private boolean canRun(String actionId) {
        if (Texts.isBlank(actionId) || deferredOperations == null || !security.allowActionDispatch()) {
            return false;
        }
        return !security.deniedActionsFromScript().contains(Texts.lower(actionId));
    }

    private String firstToken(String line) {
        String trimmed = Texts.trim(line);
        while (trimmed.startsWith("@")) {
            int space = trimmed.indexOf(' ');
            if (space < 0) {
                return "";
            }
            trimmed = Texts.trim(trimmed.substring(space + 1));
        }
        int space = trimmed.indexOf(' ');
        return Texts.lower(space < 0 ? trimmed : trimmed.substring(0, space));
    }
}
