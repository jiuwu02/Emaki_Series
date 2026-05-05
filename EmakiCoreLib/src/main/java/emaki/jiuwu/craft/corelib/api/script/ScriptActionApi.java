package emaki.jiuwu.craft.corelib.api.script;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptActionApi {

    private static final String DEPTH_KEY = "emaki.script.actionDepth";

    private final ActionExecutor actionExecutor;
    private final ActionContext context;
    private final ScriptConfig.Security security;

    public ScriptActionApi(ActionExecutor actionExecutor, ActionContext context, ScriptConfig.Security security) {
        this.actionExecutor = actionExecutor;
        this.context = context;
        this.security = security == null ? ScriptConfig.Security.defaults() : security;
    }

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
        return withDepth(() -> {
            ActionResult result = actionExecutor.execute(context, actionId, resolved).join();
            return result != null && result.success() && !result.skipped();
        });
    }

    public boolean runLine(String line) {
        if (Texts.isBlank(line) || actionExecutor == null || context == null || !security.allowActionDispatch()) {
            return false;
        }
        String actionId = firstToken(line);
        if (!canRun(actionId)) {
            return false;
        }
        return withDepth(() -> actionExecutor.executeAll(context, List.of(line), true).join().success());
    }

    private boolean canRun(String actionId) {
        if (Texts.isBlank(actionId) || actionExecutor == null || context == null || !security.allowActionDispatch()) {
            return false;
        }
        return !security.deniedActionsFromScript().contains(Texts.lower(actionId));
    }

    private boolean withDepth(java.util.concurrent.Callable<Boolean> callable) {
        int depth = currentDepth();
        if (security.maxActionDepth() > 0 && depth >= security.maxActionDepth()) {
            return false;
        }
        context.sharedState().put(DEPTH_KEY, depth + 1);
        try {
            return Boolean.TRUE.equals(callable.call());
        } catch (Exception _) {
            return false;
        } finally {
            if (depth <= 0) {
                context.sharedState().remove(DEPTH_KEY);
            } else {
                context.sharedState().put(DEPTH_KEY, depth);
            }
        }
    }

    private int currentDepth() {
        Object raw = context == null ? null : context.sharedValue(DEPTH_KEY);
        return raw instanceof Number number ? Math.max(0, number.intValue()) : 0;
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
