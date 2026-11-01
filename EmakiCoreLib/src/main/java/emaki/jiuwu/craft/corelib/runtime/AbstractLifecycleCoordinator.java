package emaki.jiuwu.craft.corelib.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.log.EmakiLog;

public abstract class AbstractLifecycleCoordinator<P, C extends RuntimeComponents> {

    public abstract C initialize(P plugin);

    protected final void notifyProgress(Consumer<String> progressListener, String message) {
        if (progressListener == null || message == null || message.isBlank()) {
            return;
        }
        progressListener.accept(message);
    }

    protected final void runReloadStage(String stageName,
            Runnable stage,
            BiConsumer<String, Exception> failureHandler) {
        try {
            stage.run();
        } catch (RuntimeException exception) {
            if (failureHandler != null) {
                try {
                    failureHandler.accept(stageName, exception);
                } catch (RuntimeException handlerFailure) {
                    exception.addSuppressed(handlerFailure);
                }
            }
            throw exception;
        }
    }

    protected final <T> CompletableFuture<T> runReloadStageAsync(AsyncTaskScheduler scheduler, ReloadStageConfig<T> config) {
        if (config == null) {
            return CompletableFuture.completedFuture(null);
        }
        notifyProgress(config.progressListener(), config.progressMessage());
        if (scheduler == null) {
            try {
                runReloadStage(config.stageName(), config.stage(), config.failureHandler());
                return CompletableFuture.completedFuture(config.passthrough());
            } catch (RuntimeException exception) {
                return failedFuture(exception);
            }
        }
        String taskPrefix = config.taskPrefix();
        String taskName = (taskPrefix == null || taskPrefix.isBlank() ? "reload" : taskPrefix) + "-" + config.stageName();
        return scheduler.supplyAsync(taskName, () -> {
            runReloadStage(config.stageName(), config.stage(), config.failureHandler());
            return config.passthrough();
        });
    }

    protected final <L, R> CompletableFuture<R> runReloadPipelineAsync(AsyncTaskScheduler scheduler,
            ExecutionDispatcher executionDispatcher,
            Plugin executionOwner,
            ReloadPipelineConfig<L, R> config) {
        if (config == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (scheduler == null) {
            try {
                notifyProgress(config.progressListener(), config.loadProgressMessage());
                L loaded = config.asyncLoad() == null ? null : config.asyncLoad().get();
                notifyProgress(config.progressListener(), config.applyProgressMessage());
                R result = config.syncApply() == null ? null : config.syncApply().apply(loaded);
                if (config.postRefresh() != null) {
                    notifyProgress(config.progressListener(), config.postRefreshProgressMessage());
                    config.postRefresh().accept(result);
                }
                return CompletableFuture.completedFuture(result);
            } catch (Exception exception) {
                handleReloadPipelineFailure(config, config.applyStageName(), exception, executionOwner);
                return failedFuture(exception);
            }
        }
        if (executionDispatcher == null || executionOwner == null) {
            IllegalStateException exception = new IllegalStateException(
                    "重载管道需要为全局应用阶段提供 ExecutionDispatcher 与所属插件");
            handleReloadPipelineFailure(config, config.applyStageName(), exception, executionOwner);
            return failedFuture(exception);
        }
        String prefix = config.taskPrefix() == null || config.taskPrefix().isBlank() ? "reload" : config.taskPrefix();
        notifyProgress(config.progressListener(), config.loadProgressMessage());
        return scheduler.supplyAsync(prefix + "-" + config.loadStageName(), () -> {
            try {
                return config.asyncLoad() == null ? null : config.asyncLoad().get();
            } catch (Exception exception) {
                handleReloadPipelineFailure(config, config.loadStageName(), exception, executionOwner);
                throw new CompletionException(exception);
            }
        }).thenCompose(loaded -> runReloadApplyOnGlobal(executionDispatcher, executionOwner, loaded, config));
    }

    private <L, R> CompletableFuture<R> runReloadApplyOnGlobal(ExecutionDispatcher executionDispatcher,
            Plugin executionOwner,
            L loaded,
            ReloadPipelineConfig<L, R> config) {
        CompletableFuture<R> future = new CompletableFuture<>();
        notifyProgress(config.progressListener(), config.applyProgressMessage());
        Runnable task = () -> {
            try {
                R result = config.syncApply() == null ? null : config.syncApply().apply(loaded);
                if (config.postRefresh() != null) {
                    notifyProgress(config.progressListener(), config.postRefreshProgressMessage());
                    config.postRefresh().accept(result);
                }
                future.complete(result);
            } catch (Exception exception) {
                handleReloadPipelineFailure(config, config.applyStageName(), exception, executionOwner);
                future.completeExceptionally(exception);
            }
        };
        try {
            if (executionDispatcher.runGlobal(executionOwner, task) == null) {
                RejectedExecutionException exception = new RejectedExecutionException(
                        "重载管道的全局应用阶段被拒绝");
                handleReloadPipelineFailure(config, config.applyStageName(), exception, executionOwner);
                future.completeExceptionally(exception);
            }
        } catch (RuntimeException exception) {
            handleReloadPipelineFailure(config, config.applyStageName(), exception, executionOwner);
            future.completeExceptionally(exception);
        }
        return future;
    }

    private <L, R> void handleReloadPipelineFailure(ReloadPipelineConfig<L, R> config,
            String stageName,
            Exception exception,
            Plugin rollbackLogOwner) {
        if (config.rollback() != null) {
            try {
                config.rollback().accept(exception);
            } catch (Exception rollbackFailure) {
                logRollbackFailure(rollbackLogOwner, stageName, exception, rollbackFailure);
            }
        }
        if (config.failureHandler() != null) {
            config.failureHandler().accept(stageName, exception);
        }
    }

    private void logRollbackFailure(Plugin rollbackLogOwner,
            String stageName,
            Exception stageFailure,
            Exception rollbackFailure) {
        Logger logger = rollbackLogOwner == null ? EmakiLog.of("EmakiCoreLib") : rollbackLogOwner.getLogger();
        logger.log(Level.SEVERE,
                "[reload] 阶段失败后重载回滚失败；模块可能既未应用新配置也未应用旧配置: stage=" + stageName
                        + ", stageFailure=" + stageFailure
                        + ", rollbackFailure=" + rollbackFailure,
                rollbackFailure);
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    public record ReloadStageConfig<T>(String taskPrefix,
            String stageName,
            String progressMessage,
            Consumer<String> progressListener,
            Runnable stage,
            T passthrough,
            BiConsumer<String, Exception> failureHandler) {

    }

    public record ReloadPipelineConfig<L, R>(String taskPrefix,
            String loadStageName,
            String loadProgressMessage,
            Supplier<L> asyncLoad,
            String applyStageName,
            String applyProgressMessage,
            Function<L, R> syncApply,
            String postRefreshProgressMessage,
            Consumer<R> postRefresh,
            Consumer<Throwable> rollback,
            BiConsumer<String, Exception> failureHandler,
            Consumer<String> progressListener) {

    }
}
