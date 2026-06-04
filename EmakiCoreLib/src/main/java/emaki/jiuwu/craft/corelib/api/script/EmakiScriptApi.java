package emaki.jiuwu.craft.corelib.api.script;

import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.Plugin;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.api.script.modules.ScriptCoreLibModuleApi;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.ScriptModuleRegistry;

public final class EmakiScriptApi {

    public final ScriptContextApi context;
    public final ScriptPlayerApi player;
    public final ScriptItemApi item;
    public final ScriptActionApi action;
    public final ScriptLoggerApi logger;
    public final ScriptRandomApi random;
    public final ScriptSharedStateApi state;
    public final ScriptTextApi text;
    public final ScriptCoreLibModuleApi corelib;
    public final ScriptModuleRegistry.ScriptModulesApi modules;
    public final ScriptServerApi server;

    private final Plugin sourcePlugin;
    private final ScriptModuleContext moduleContext;
    private final ScriptModuleRegistry moduleRegistry;

    public EmakiScriptApi(ActionContext context,
            java.util.Map<String, Object> arguments,
            ActionExecutor actionExecutor,
            ScriptConfig config,
            String scriptPath) {
        this(context, arguments, actionExecutor, config, scriptPath, context == null ? null : context.sourcePlugin(), null);
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
        ScriptConfig safeConfig = config == null ? ScriptConfig.defaults() : config;
        this.sourcePlugin = sourcePlugin == null && context != null ? context.sourcePlugin() : sourcePlugin;
        this.context = safeConfig.context().exposeContext() ? new ScriptContextApi(context, arguments) : null;
        this.player = safeConfig.context().exposePlayer() ? new ScriptPlayerApi(context) : null;
        this.item = safeConfig.context().exposeItem() ? new ScriptItemApi(context) : null;
        this.action = safeConfig.context().exposeAction() ? new ScriptActionApi(actionExecutor, context, safeConfig.security()) : null;
        this.logger = safeConfig.context().exposeLogger() ? new ScriptLoggerApi(this.sourcePlugin, scriptPath) : null;
        this.random = safeConfig.context().exposeRandom() ? new ScriptRandomApi() : null;
        this.state = safeConfig.context().exposeSharedState() ? new ScriptSharedStateApi(context) : null;
        this.text = safeConfig.context().exposeText() ? new ScriptTextApi() : null;
        this.corelib = new ScriptCoreLibModuleApi();
        this.moduleRegistry = moduleRegistry == null ? new ScriptModuleRegistry() : moduleRegistry;
        this.moduleContext = new ScriptModuleContext(context, arguments, actionExecutor, safeConfig, scriptPath, this.sourcePlugin);
        this.modules = this.moduleRegistry.api(this.moduleContext);
        this.server = safeConfig.serverApi().enabled() ? new ScriptServerApi(this.sourcePlugin, safeConfig) : null;
    }

    @HostAccess.Export
    public Object module(String id) {
        return modules.get(id);
    }

    @HostAccess.Export
    public void runSync(Runnable task) {
        if (task == null) {
            return;
        }
        FoliaSchedulerAdapter.runTask(sourcePlugin, task);
    }

    @HostAccess.Export
    public CompletableFuture<Void> runSyncAndWait(Runnable task) {
        if (task == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        FoliaSchedulerAdapter.runTask(sourcePlugin, () -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }
}
