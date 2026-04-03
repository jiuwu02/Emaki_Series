package emaki.jiuwu.craft.attribute.service;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import emaki.jiuwu.craft.attribute.model.DamageRequest;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;

final class AsyncDamageEngine {

    private static final long DEFAULT_TIMEOUT_MILLIS = 5_000L;

    private final AsyncTaskScheduler scheduler;
    private final DamageEngine damageEngine;
    private final long timeoutMillis;

    AsyncDamageEngine(AsyncTaskScheduler scheduler, DamageEngine damageEngine) {
        this(scheduler, damageEngine, DEFAULT_TIMEOUT_MILLIS);
    }

    AsyncDamageEngine(AsyncTaskScheduler scheduler, DamageEngine damageEngine, long timeoutMillis) {
        this.scheduler = scheduler;
        this.damageEngine = Objects.requireNonNull(damageEngine, "damageEngine");
        this.timeoutMillis = timeoutMillis <= 0L ? DEFAULT_TIMEOUT_MILLIS : timeoutMillis;
    }

    CompletableFuture<DamageResult> resolveAsync(DamageRequest request, DamageTypeDefinition definition, double seededRoll) {
        if (scheduler == null) {
            return CompletableFuture.completedFuture(damageEngine.resolve(request, definition, seededRoll));
        }
        return scheduler.supplyAsync(
                "attribute-damage-engine",
                timeoutMillis,
                () -> damageEngine.resolve(request, definition, seededRoll)
        );
    }
}
