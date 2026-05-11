package emaki.jiuwu.craft.corelib.api.script;

import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.graalvm.polyglot.HostAccess;

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

    private final Plugin sourcePlugin;

    public EmakiScriptApi(ActionContext context,
            java.util.Map<String, Object> arguments,
            ActionExecutor actionExecutor,
            ScriptConfig config,
            String scriptPath) {
        ScriptConfig safeConfig = config == null ? ScriptConfig.defaults() : config;
        this.sourcePlugin = context == null ? null : context.sourcePlugin();
        this.context = safeConfig.context().exposeContext() ? new ScriptContextApi(context, arguments) : null;
        this.player = safeConfig.context().exposePlayer() ? new ScriptPlayerApi(context) : null;
        this.item = safeConfig.context().exposeItem() ? new ScriptItemApi(context) : null;
        this.action = safeConfig.context().exposeAction() ? new ScriptActionApi(actionExecutor, context, safeConfig.security()) : null;
        this.logger = safeConfig.context().exposeLogger() ? new ScriptLoggerApi(context == null ? null : context.sourcePlugin(), scriptPath) : null;
        this.random = safeConfig.context().exposeRandom() ? new ScriptRandomApi() : null;
        this.state = safeConfig.context().exposeSharedState() ? new ScriptSharedStateApi(context) : null;
        this.text = safeConfig.context().exposeText() ? new ScriptTextApi() : null;
    }

    /**
     * Schedules a task to run on the main server thread.
     * Use this when calling Bukkit API from an async script execution context.
     * The task is dispatched via BukkitScheduler.runTask and executes on the next tick.
     *
     * @param task the Runnable to execute on the main thread
     */
    @HostAccess.Export
    public void runSync(Runnable task) {
        if (task == null) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        if (sourcePlugin == null || !sourcePlugin.isEnabled()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(sourcePlugin, task);
    }

    /**
     * Schedules a task on the main thread and returns a CompletableFuture that completes
     * when the task finishes. Useful for scripts that need to await a sync result.
     *
     * @param task the Runnable to execute on the main thread
     * @return a CompletableFuture that completes when the task is done
     */
    @HostAccess.Export
    public CompletableFuture<Void> runSyncAndWait(Runnable task) {
        if (task == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (sourcePlugin == null || !sourcePlugin.isEnabled()) {
            task.run();
            future.complete(null);
            return future;
        }
        Bukkit.getScheduler().runTask(sourcePlugin, () -> {
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
