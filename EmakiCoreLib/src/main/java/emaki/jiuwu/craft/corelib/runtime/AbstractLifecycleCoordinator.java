package emaki.jiuwu.craft.corelib.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
}
