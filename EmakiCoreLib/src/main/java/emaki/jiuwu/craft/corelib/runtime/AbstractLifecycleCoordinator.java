package emaki.jiuwu.craft.corelib.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;

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

    protected final <T> CompletableFuture<T> runReloadStageAsync(AsyncTaskScheduler scheduler,
            String taskPrefix,
            String stageName,
            String progressMessage,
            Consumer<String> progressListener,
            Runnable stage,
            T passthrough,
            BiConsumer<String, Exception> failureHandler) {
        notifyProgress(progressListener, progressMessage);
        if (scheduler == null) {
            runReloadStage(stageName, stage, failureHandler);
            return CompletableFuture.completedFuture(passthrough);
        }
        String taskName = (taskPrefix == null || taskPrefix.isBlank() ? "reload" : taskPrefix) + "-" + stageName;
        return scheduler.supplyAsync(taskName, () -> {
            runReloadStage(stageName, stage, failureHandler);
            return passthrough;
        });
    }
}
