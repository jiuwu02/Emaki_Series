package emaki.jiuwu.craft.corelib.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;

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
        } catch (Exception exception) {
            if (failureHandler != null) {
                failureHandler.accept(stageName, exception);
            }
        }
    }

    protected final <T> CompletableFuture<T> runReloadStageAsync(AsyncTaskScheduler scheduler, ReloadStageConfig<T> config) {
        if (config == null) {
            return CompletableFuture.completedFuture(null);
        }
        notifyProgress(config.progressListener(), config.progressMessage());
        if (scheduler == null) {
            runReloadStage(config.stageName(), config.stage(), config.failureHandler());
            return CompletableFuture.completedFuture(config.passthrough());
        }
        String taskPrefix = config.taskPrefix();
        String taskName = (taskPrefix == null || taskPrefix.isBlank() ? "reload" : taskPrefix) + "-" + config.stageName();
        return scheduler.supplyAsync(taskName, () -> {
            runReloadStage(config.stageName(), config.stage(), config.failureHandler());
            return config.passthrough();
        });
    }

    protected final <L, R> CompletableFuture<R> runReloadPipelineAsync(AsyncTaskScheduler scheduler,
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
                handleReloadPipelineFailure(config, config.applyStageName(), exception);
                return failedFuture(exception);
            }
        }
        String prefix = config.taskPrefix() == null || config.taskPrefix().isBlank() ? "reload" : config.taskPrefix();
        notifyProgress(config.progressListener(), config.loadProgressMessage());
        return scheduler.supplyAsync(prefix + "-" + config.loadStageName(), () -> {
            try {
                return config.asyncLoad() == null ? null : config.asyncLoad().get();
            } catch (Exception exception) {
                handleReloadPipelineFailure(config, config.loadStageName(), exception);
                throw new java.util.concurrent.CompletionException(exception);
            }
        }).thenCompose(loaded -> {
            notifyProgress(config.progressListener(), config.applyProgressMessage());
            return scheduler.callSync(prefix + "-" + config.applyStageName(), () -> {
                try {
                    R result = config.syncApply() == null ? null : config.syncApply().apply(loaded);
                    if (config.postRefresh() != null) {
                        notifyProgress(config.progressListener(), config.postRefreshProgressMessage());
                        config.postRefresh().accept(result);
                    }
                    return result;
                } catch (Exception exception) {
                    handleReloadPipelineFailure(config, config.applyStageName(), exception);
                    throw new java.util.concurrent.CompletionException(exception);
                }
            });
        });
    }

    private <L, R> void handleReloadPipelineFailure(ReloadPipelineConfig<L, R> config, String stageName, Exception exception) {
        if (config.rollback() != null) {
            try {
                config.rollback().accept(exception);
            } catch (Exception ignored) {
            }
        }
        if (config.failureHandler() != null) {
            config.failureHandler().accept(stageName, exception);
        }
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    protected final void syncPdcAttributeRegistration(PdcAttributeGateway gateway, String sourceId) {
        if (gateway == null || sourceId == null || sourceId.isBlank()) {
            return;
        }
        gateway.syncRegistration(sourceId);
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
