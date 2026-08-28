package emaki.jiuwu.craft.mobs.command;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.mobs.EmakiMobsPlugin;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToIntFunction;

final class ManagedMobCommandService {

    private final EmakiMobsPlugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final MobIdentifier mobIdentifier;

    ManagedMobCommandService(EmakiMobsPlugin plugin) {
        this.plugin = plugin;
        this.dispatcher = plugin.executionDispatcher();
        this.mobIdentifier = plugin.mobIdentifier();
    }

    CompletableFuture<Integer> count(String mobId) {
        Set<String> loadedIds = Set.copyOf(plugin.mobRegistry().get().keySet());
        return inspect(entity -> {
            String managedId = mobIdentifier.readId(entity);
            if (managedId == null || !loadedIds.contains(managedId)) {
                return 0;
            }
            return mobId == null || mobId.equals(managedId) ? 1 : 0;
        });
    }

    CompletableFuture<Integer> kill(String mobId, Location center, double radius) {
        Location fixedCenter = center == null ? null : center.clone();
        double radiusSquared = radius < 0D ? -1D : radius * radius;
        return inspect(entity -> {
            String managedId = mobIdentifier.readId(entity);
            if (!mobId.equals(managedId)) {
                return 0;
            }
            if (fixedCenter != null) {
                if (entity.getWorld() != fixedCenter.getWorld()
                        || entity.getLocation().distanceSquared(fixedCenter) > radiusSquared) {
                    return 0;
                }
            }
            entity.remove();
            mobIdentifier.forget(entity);
            return 1;
        });
    }

    private CompletableFuture<Integer> inspect(ToIntFunction<LivingEntity> operation) {
        List<LivingEntity> candidates = mobIdentifier.trackedEntities();
        if (candidates.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        var result = new CompletableFuture<Integer>();
        var total = new AtomicInteger();
        var remaining = new AtomicInteger(candidates.size());
        var failure = new AtomicReference<Throwable>();
        for (LivingEntity entity : candidates) {
            var completed = new AtomicBoolean();
            Runnable retired = () -> {
                mobIdentifier.forget(entity);
                completeCandidate(completed, remaining, total, failure, result, null);
            };
            TaskToken token = dispatcher.runEntity(plugin, entity, () -> {
                Throwable throwable = null;
                try {
                    if (!plugin.isEnabled() || plugin.isShutdownStarted()) {
                        throw new IllegalStateException("EmakiMobs is shutting down");
                    }
                    if (!entity.isValid() || entity.isDead()) {
                        mobIdentifier.forget(entity);
                    } else {
                        total.addAndGet(operation.applyAsInt(entity));
                    }
                } catch (RuntimeException exception) {
                    throwable = exception;
                }
                completeCandidate(completed, remaining, total, failure, result, throwable);
            }, retired);
            if (token == null || token.cancelled()) {
                completeCandidate(completed, remaining, total, failure, result,
                        new IllegalStateException("Unable to schedule managed mob inspection"));
            }
        }
        return result;
    }

    private void completeCandidate(AtomicBoolean completed,
                                   AtomicInteger remaining,
                                   AtomicInteger total,
                                   AtomicReference<Throwable> failure,
                                   CompletableFuture<Integer> result,
                                   Throwable throwable) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        if (throwable != null) {
            failure.compareAndSet(null, throwable);
        }
        if (remaining.decrementAndGet() != 0) {
            return;
        }
        Throwable finalFailure = failure.get();
        if (finalFailure == null) {
            result.complete(total.get());
        } else {
            result.completeExceptionally(finalFailure);
        }
    }
}
