package emaki.jiuwu.craft.corelib.api.script;

import org.bukkit.plugin.Plugin;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.api.script.modules.ScriptCoreLibModuleApi;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptDeferredOperationQueue;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.ScriptModuleRegistry;

public final class EmakiScriptApi {

    @HostAccess.Export
    public final ScriptContextApi context;
    @HostAccess.Export
    public final ScriptPlayerApi player;
    @HostAccess.Export
    public final ScriptItemApi item;
    @HostAccess.Export
    public final ScriptActionApi action;
    @HostAccess.Export
    public final ScriptLoggerApi logger;
    @HostAccess.Export
    public final ScriptRandomApi random;
    @HostAccess.Export
    public final ScriptSharedStateApi state;
    @HostAccess.Export
    public final ScriptTextApi text;
    @HostAccess.Export
    public final ScriptCoreLibModuleApi corelib;
    @HostAccess.Export
    public final ScriptModuleRegistry.ScriptModulesApi modules;
    @HostAccess.Export
    public final ScriptServerApi server;

    private final Plugin sourcePlugin;
    private final ScriptModuleContext moduleContext;
    private final ScriptModuleRegistry moduleRegistry;
    private final ScriptDeferredOperationQueue deferredOperations;

    public EmakiScriptApi(ActionContext context,
            java.util.Map<String, Object> arguments,
            ActionExecutor actionExecutor,
            ScriptConfig config,
            String scriptPath) {
        this(context, arguments, actionExecutor, config, scriptPath,
                context == null ? null : context.sourcePlugin(), null);
    }

    public EmakiScriptApi(ActionContext context,
            java.util.Map<String, Object> arguments,
            ActionExecutor actionExecutor,
            ScriptConfig config,
            String scriptPath,
            Plugin sourcePlugin) {
        this(context, arguments, actionExecutor, config, scriptPath, sourcePlugin, null);
    }

    public EmakiScriptApi(ActionContext context,
            java.util.Map<String, Object> arguments,
            ActionExecutor actionExecutor,
            ScriptConfig config,
            String scriptPath,
            Plugin sourcePlugin,
            ScriptModuleRegistry moduleRegistry) {
        this(context, arguments, actionExecutor, config, scriptPath, sourcePlugin, moduleRegistry,
                java.util.Map.of());
    }

    public EmakiScriptApi(ActionContext context,
            java.util.Map<String, Object> arguments,
            ActionExecutor actionExecutor,
            ScriptConfig config,
            String scriptPath,
            Plugin sourcePlugin,
            ScriptModuleRegistry moduleRegistry,
            java.util.Map<String, Object> moduleOverrides) {
        this(context, arguments, actionExecutor, config, scriptPath, sourcePlugin, moduleRegistry,
                moduleOverrides, new ScriptDeferredOperationQueue(
                        sourcePlugin == null && context != null ? context.sourcePlugin() : sourcePlugin,
                        actionExecutor,
                        context
                ));
    }

    public EmakiScriptApi(ActionContext context,
            java.util.Map<String, Object> arguments,
            ActionExecutor actionExecutor,
            ScriptConfig config,
            String scriptPath,
            Plugin sourcePlugin,
            ScriptModuleRegistry moduleRegistry,
            java.util.Map<String, Object> moduleOverrides,
            ScriptDeferredOperationQueue deferredOperations) {
        ScriptConfig safeConfig = config == null ? ScriptConfig.defaults() : config;
        this.sourcePlugin = sourcePlugin == null && context != null ? context.sourcePlugin() : sourcePlugin;
        this.deferredOperations = deferredOperations == null
                ? new ScriptDeferredOperationQueue(this.sourcePlugin, actionExecutor, context)
                : deferredOperations;
        this.context = safeConfig.context().exposeContext() ? new ScriptContextApi(context, arguments) : null;
        this.player = safeConfig.context().exposePlayer() ? new ScriptPlayerApi(context) : null;
        this.item = safeConfig.context().exposeItem() ? new ScriptItemApi(context) : null;
        this.action = safeConfig.context().exposeAction()
                ? new ScriptActionApi(this.deferredOperations, safeConfig.security())
                : null;
        this.logger = safeConfig.context().exposeLogger() ? new ScriptLoggerApi(this.sourcePlugin, scriptPath) : null;
        this.random = safeConfig.context().exposeRandom() ? new ScriptRandomApi() : null;
        this.state = safeConfig.context().exposeSharedState()
                ? new ScriptSharedStateApi(context, this.deferredOperations)
                : null;
        this.text = safeConfig.context().exposeText()
                ? new ScriptTextApi(this.sourcePlugin, this.deferredOperations)
                : null;
        this.corelib = new ScriptCoreLibModuleApi();
        this.moduleRegistry = moduleRegistry == null ? new ScriptModuleRegistry() : moduleRegistry;
        this.moduleContext = new ScriptModuleContext(
                context,
                arguments,
                actionExecutor,
                safeConfig,
                scriptPath,
                this.sourcePlugin,
                moduleOverrides
        );
        this.modules = ScriptDeferredOperationQueue.withModuleCapture(
                this.deferredOperations,
                context,
                () -> this.moduleRegistry.api(this.moduleContext)
        );
        this.server = safeConfig.serverApi().enabled()
                ? new ScriptServerApi(this.sourcePlugin, safeConfig, this.deferredOperations)
                : null;
    }

    @HostAccess.Export
    public Object module(String id) {
        return modules.get(id);
    }

    public ScriptDeferredOperationQueue deferredOperations() {
        return deferredOperations;
    }
}
