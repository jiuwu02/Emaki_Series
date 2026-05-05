package emaki.jiuwu.craft.corelib.api.script;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;

public final class EmakiScriptApi {

    public final ScriptContextApi context;
    public final ScriptPlayerApi player;
    public final ScriptItemApi item;
    public final ScriptActionApi action;
    public final ScriptLoggerApi logger;
    public final ScriptRandomApi random;
    public final ScriptSharedStateApi state;
    public final ScriptTextApi text;

    public EmakiScriptApi(ActionContext context,
            java.util.Map<String, Object> arguments,
            ActionExecutor actionExecutor,
            ScriptConfig config,
            String scriptPath) {
        ScriptConfig safeConfig = config == null ? ScriptConfig.defaults() : config;
        this.context = safeConfig.context().exposeContext() ? new ScriptContextApi(context, arguments) : null;
        this.player = safeConfig.context().exposePlayer() ? new ScriptPlayerApi(context) : null;
        this.item = safeConfig.context().exposeItem() ? new ScriptItemApi(context) : null;
        this.action = safeConfig.context().exposeAction() ? new ScriptActionApi(actionExecutor, context, safeConfig.security()) : null;
        this.logger = safeConfig.context().exposeLogger() ? new ScriptLoggerApi(context == null ? null : context.sourcePlugin(), scriptPath) : null;
        this.random = safeConfig.context().exposeRandom() ? new ScriptRandomApi() : null;
        this.state = safeConfig.context().exposeSharedState() ? new ScriptSharedStateApi(context) : null;
        this.text = safeConfig.context().exposeText() ? new ScriptTextApi() : null;
    }
}
