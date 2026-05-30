package emaki.jiuwu.craft.forge.service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;

final class ForgeAsyncExecutor {

    private final AsyncTaskScheduler asyncTaskScheduler;

    ForgeAsyncExecutor(AsyncTaskScheduler asyncTaskScheduler) {
        this.asyncTaskScheduler = asyncTaskScheduler;
    }

    <T> CompletableFuture<T> supplyPrepare(Supplier<T> supplier) {
        if (asyncTaskScheduler == null) {
            return CompletableFuture.completedFuture(supplier.get());
        }
        return asyncTaskScheduler.supplyAsync(
                "forge-prepare",
                AsyncTaskScheduler.TaskPriority.NORMAL,
                10_000L,
                supplier
        );
    }
}
