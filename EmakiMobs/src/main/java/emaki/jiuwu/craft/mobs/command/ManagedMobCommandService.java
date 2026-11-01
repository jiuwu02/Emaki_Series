package emaki.jiuwu.craft.mobs.command;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.mobs.EmakiMobsPlugin;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

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
        return collectManaged(entity -> {
            String managedId = mobIdentifier.readId(entity);
            if (managedId == null || !loadedIds.contains(managedId)) {
                return null;
            }
            return mobId == null || mobId.equals(managedId) ? Boolean.TRUE : null;
        }).thenApply(matched -> matched.size());
    }

    CompletableFuture<Integer> kill(String mobId, Location center, double radius) {
        Location fixedCenter = center == null ? null : center.clone();
        double radiusSquared = radius < 0D ? -1D : radius * radius;
        return collectManaged(entity -> {
            String managedId = mobIdentifier.readId(entity);
            if (managedId == null || (mobId != null && !mobId.equals(managedId))) {
                return null;
            }
            if (fixedCenter != null) {
                Location current = entity.getLocation();
                if (current.getWorld() != fixedCenter.getWorld()
                        || current.distanceSquared(fixedCenter) > radiusSquared) {
                    return null;
                }
            }
            entity.remove();
            mobIdentifier.forget(entity);
            return Boolean.TRUE;
        }).thenApply(removed -> removed.size());
    }

    record Match(UUID uuid, String mobId, double distanceSquared,
                 String worldName, int blockX, int blockY, int blockZ,
                 double health, double maxHealth) {
    }

    CompletableFuture<List<Match>> find(String mobId, Location center, double radius) {
        Location fixedCenter = center == null ? null : center.clone();
        boolean filterByRadius = fixedCenter != null && radius >= 0D;
        double radiusSquared = filterByRadius ? radius * radius : -1D;
        return collectManaged(entity -> createMatch(entity, mobId, fixedCenter, filterByRadius, radiusSquared));
    }

    @SuppressWarnings("deprecation")
    private Match createMatch(LivingEntity entity, String mobId, Location center,
                              boolean filterByRadius, double radiusSquared) {
        String managedId = mobIdentifier.readId(entity);
        if (managedId == null || (mobId != null && !mobId.equals(managedId))) {
            return null;
        }
        Location location = entity.getLocation();
        boolean sameWorld = center != null && location.getWorld() == center.getWorld();
        double distanceSquared = sameWorld ? location.distanceSquared(center) : 0D;
        if (filterByRadius && (!sameWorld || distanceSquared > radiusSquared)) {
            return null;
        }
        return new Match(entity.getUniqueId(), managedId, distanceSquared,
                location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                entity.getHealth(), entity.getMaxHealth());
    }

    private CompletableFuture<List<LivingEntity>> discoverCandidates() {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        var result = new CompletableFuture<List<LivingEntity>>();
        Queue<LivingEntity> candidates = new ConcurrentLinkedQueue<>();
        var remaining = new AtomicInteger(worlds.size());
        var failure = new AtomicReference<Throwable>();
        for (World world : worlds) {
            var completed = new AtomicBoolean();
            Runnable settle = () -> {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                if (remaining.decrementAndGet() != 0) {
                    return;
                }
                Throwable finalFailure = failure.get();
                if (finalFailure == null) {
                    result.complete(List.copyOf(candidates));
                } else {
                    result.completeExceptionally(finalFailure);
                }
            };
            TaskToken token = schedule(() -> dispatcher.runAtLocation(plugin, worldAnchor(world), () -> {
                try {
                    if (!plugin.isEnabled() || plugin.isShutdownStarted()) {
                        throw new IllegalStateException("EmakiMobs 正在关闭");
                    }
                    for (Entity entity : world.getEntities()) {
                        if (entity instanceof LivingEntity living && mobIdentifier.isManaged(living)) {
                            candidates.add(living);
                        }
                    }
                } catch (RuntimeException exception) {
                    failure.compareAndSet(null, exception);
                }
                settle.run();
            }), "世界 '" + world.getName() + "' 的受管生物枚举");
            if (token == null || token.cancelled()) {
                failure.compareAndSet(null, new IllegalStateException("无法调度受管生物枚举"));
                settle.run();
            }
        }
        return result;
    }

    private <T> CompletableFuture<List<T>> collectManaged(Function<LivingEntity, T> operation) {
        var result = new CompletableFuture<List<T>>();
        discoverCandidates().whenComplete((candidates, discoveryFailure) -> {
            if (discoveryFailure != null) {
                result.completeExceptionally(discoveryFailure);
                return;
            }
            if (candidates.isEmpty()) {
                result.complete(List.of());
                return;
            }
            Queue<T> collected = new ConcurrentLinkedQueue<>();
            var remaining = new AtomicInteger(candidates.size());
            var failure = new AtomicReference<Throwable>();
            for (LivingEntity entity : candidates) {
                var completed = new AtomicBoolean();
                Runnable retired = () -> {
                    mobIdentifier.forget(entity);
                    completeCandidate(completed, remaining, failure, result, collected, null, null);
                };
                TaskToken token = schedule(() -> dispatcher.runEntity(plugin, entity, () -> {
                    Throwable throwable = null;
                    T value = null;
                    try {
                        if (!plugin.isEnabled() || plugin.isShutdownStarted()) {
                            throw new IllegalStateException("EmakiMobs 正在关闭");
                        }
                        if (entity.isValid() && !entity.isDead()) {
                            value = operation.apply(entity);
                        } else {
                            mobIdentifier.forget(entity);
                        }
                    } catch (RuntimeException exception) {
                        throwable = exception;
                    }
                    completeCandidate(completed, remaining, failure, result, collected, value, throwable);
                }, retired), "受管生物检查");
                if (token == null || token.cancelled()) {
                    completeCandidate(completed, remaining, failure, result, collected, null,
                            new IllegalStateException("无法调度受管生物检查"));
                }
            }
        });
        return result;
    }

    private <T> void completeCandidate(AtomicBoolean completed,
                                       AtomicInteger remaining,
                                       AtomicReference<Throwable> failure,
                                       CompletableFuture<List<T>> result,
                                       Queue<T> collected,
                                       T value,
                                       Throwable throwable) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        if (throwable != null) {
            failure.compareAndSet(null, throwable);
        } else if (value != null) {
            collected.add(value);
        }
        if (remaining.decrementAndGet() != 0) {
            return;
        }
        Throwable finalFailure = failure.get();
        if (finalFailure == null) {
            result.complete(List.copyOf(collected));
        } else {
            result.completeExceptionally(finalFailure);
        }
    }

    private TaskToken schedule(Supplier<TaskToken> action, String description) {
        try {
            return action.get();
        } catch (RuntimeException exception) {
            if (plugin.isEnabled() && !plugin.isShutdownStarted()) {
                plugin.getLogger().log(Level.WARNING, "[command] 无法调度" + description, exception);
            }
            return null;
        }
    }

    private Location worldAnchor(World world) {
        return new Location(world, 0.5D, 0.0D, 0.5D);
    }
}
