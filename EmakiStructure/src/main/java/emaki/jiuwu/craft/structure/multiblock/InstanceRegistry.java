package emaki.jiuwu.craft.structure.multiblock;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.config.AppConfig;
import emaki.jiuwu.craft.structure.generate.StructureFacing;
import emaki.jiuwu.craft.structure.generate.TemplateTransform;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class InstanceRegistry {

    private final EmakiStructurePlugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final Supplier<AppConfig.DetectionSettings> settings;

    private final Map<String, Map<Long, Set<MultiblockInstance>>> candidatesByChunk =
            new ConcurrentHashMap<>();
    private final Map<String, Map<Long, Set<MultiblockInstance>>> formedByChunk =
            new ConcurrentHashMap<>();
    private final Map<Long, TaskToken> debounced = new ConcurrentHashMap<>();

    public InstanceRegistry(EmakiStructurePlugin plugin,
            ExecutionDispatcher dispatcher,
            Supplier<AppConfig.DetectionSettings> settings) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.settings = settings;
    }

    public MultiblockInstance registerCandidate(String worldName, StructurePose pose, UUID owner) {
        MultiblockInstance instance = new MultiblockInstance(pose, owner);
        for (long chunkKey : chunkKeysOf(pose)) {
            bucketFor(candidatesByChunk, worldName, chunkKey).add(instance);
        }
        enforceLimit(worldName, pose.definitionId(), owner);
        return instance;
    }

    public List<MultiblockInstance> candidatesAt(String worldName, long chunkKey) {
        Map<Long, Set<MultiblockInstance>> worldIndex = candidatesByChunk.get(worldName);
        if (worldIndex == null) {
            return List.of();
        }
        Set<MultiblockInstance> bucket = worldIndex.get(chunkKey);
        return bucket == null ? List.of() : List.copyOf(bucket);
    }

    public List<MultiblockInstance> formedAt(String worldName, long chunkKey) {
        Map<Long, Set<MultiblockInstance>> worldIndex = formedByChunk.get(worldName);
        if (worldIndex == null) {
            return List.of();
        }
        Set<MultiblockInstance> bucket = worldIndex.get(chunkKey);
        return bucket == null ? List.of() : List.copyOf(bucket);
    }

    public List<MultiblockInstance> allAt(String worldName, long chunkKey) {
        List<MultiblockInstance> merged = new ArrayList<>(candidatesAt(worldName, chunkKey));
        for (MultiblockInstance instance : formedAt(worldName, chunkKey)) {
            if (!merged.contains(instance)) {
                merged.add(instance);
            }
        }
        return merged;
    }

    public List<MultiblockInstance> allFormed() {
        Set<MultiblockInstance> unique = new LinkedHashSet<>();
        for (Map<Long, Set<MultiblockInstance>> worldIndex : formedByChunk.values()) {
            for (Set<MultiblockInstance> bucket : worldIndex.values()) {
                unique.addAll(bucket);
            }
        }
        return List.copyOf(unique);
    }

    public boolean markFormed(MultiblockInstance instance) {
        if (!instance.transitionToFormed()) {
            return false;
        }
        String worldName = instance.pose().worldName();
        for (long chunkKey : chunkKeysOf(instance.pose())) {
            Map<Long, Set<MultiblockInstance>> candidateIndex = candidatesByChunk.get(worldName);
            if (candidateIndex != null) {
                Set<MultiblockInstance> bucket = candidateIndex.get(chunkKey);
                if (bucket != null) {
                    bucket.remove(instance);
                }
            }
            bucketFor(formedByChunk, worldName, chunkKey).add(instance);
        }
        return true;
    }

    public boolean degrade(MultiblockInstance instance) {
        if (!instance.degradeToUnformed()) {
            return false;
        }
        String worldName = instance.pose().worldName();
        for (long chunkKey : chunkKeysOf(instance.pose())) {
            Map<Long, Set<MultiblockInstance>> formedIndex = formedByChunk.get(worldName);
            if (formedIndex != null) {
                Set<MultiblockInstance> bucket = formedIndex.get(chunkKey);
                if (bucket != null) {
                    bucket.remove(instance);
                }
            }
            bucketFor(candidatesByChunk, worldName, chunkKey).add(instance);
        }
        return true;
    }

    public void remove(MultiblockInstance instance) {
        String worldName = instance.pose().worldName();
        for (long chunkKey : chunkKeysOf(instance.pose())) {
            removeFrom(candidatesByChunk, worldName, chunkKey, instance);
            removeFrom(formedByChunk, worldName, chunkKey, instance);
        }
    }

    private static void removeFrom(Map<String, Map<Long, Set<MultiblockInstance>>> index,
            String worldName, long chunkKey, MultiblockInstance instance) {
        Map<Long, Set<MultiblockInstance>> worldIndex = index.get(worldName);
        if (worldIndex == null) {
            return;
        }
        Set<MultiblockInstance> bucket = worldIndex.get(chunkKey);
        if (bucket == null) {
            return;
        }
        bucket.remove(instance);
        if (bucket.isEmpty()) {
            worldIndex.remove(chunkKey, bucket);
        }
    }

    private static Set<MultiblockInstance> bucketFor(
            Map<String, Map<Long, Set<MultiblockInstance>>> index, String worldName, long chunkKey) {
        Map<Long, Set<MultiblockInstance>> worldIndex =
                index.computeIfAbsent(worldName, key -> new ConcurrentHashMap<>());
        return worldIndex.computeIfAbsent(chunkKey, key -> ConcurrentHashMap.newKeySet());
    }

    public boolean scheduleDebounced(long chunkKey, Runnable check) {
        Duration debounce = settings.get().debounce();
        long delayTicks = Math.max(1L, debounce.toMillis() / 50L);
        boolean[] scheduled = { false };
        debounced.compute(chunkKey, (key, existing) -> {
            if (existing != null && !existing.cancelled()) {
                return existing;
            }
            scheduled[0] = true;
            return dispatcher.runGlobalLater(plugin, () -> {
                debounced.remove(key);
                try {
                    check.run();
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("多方块候选校验任务异常: " + exception.getMessage());
                }
            }, delayTicks);
        });
        return scheduled[0];
    }

    public int sweepStale() {
        Duration ttl = settings.get().instanceTtl();
        long ttlNanos = ttl.toNanos();
        int removed = 0;
        List<MultiblockInstance> snapshot = new ArrayList<>();
        for (Map<Long, Set<MultiblockInstance>> worldIndex : List.copyOf(candidatesByChunk.values())) {
            for (Set<MultiblockInstance> bucket : List.copyOf(worldIndex.values())) {
                snapshot.addAll(bucket);
            }
        }
        for (MultiblockInstance instance : snapshot) {
            if (instance.state() != MultiblockInstance.State.UNFORMED) {
                continue;
            }
            if (instance.idleNanos() >= ttlNanos) {
                remove(instance);
                removed++;
            }
        }
        return removed;
    }

    public int candidateInstanceCount() {
        Set<MultiblockInstance> unique = new LinkedHashSet<>();
        for (Map<Long, Set<MultiblockInstance>> worldIndex : candidatesByChunk.values()) {
            for (Set<MultiblockInstance> bucket : worldIndex.values()) {
                unique.addAll(bucket);
            }
        }
        return unique.size();
    }

    public int formedInstanceCount() {
        return allFormed().size();
    }

    public void clearAll() {
        for (TaskToken token : List.copyOf(debounced.values())) {
            if (token != null) {
                token.cancel();
            }
        }
        debounced.clear();
        candidatesByChunk.clear();
        formedByChunk.clear();
    }

    public void close() {
        clearAll();
    }

    private void enforceLimit(String worldName, String definitionId, UUID owner) {
        if (owner == null) {
            return;
        }
        int limit = settings.get().instanceLimit();
        if (limit < 1) {
            return;
        }
        Map<Long, Set<MultiblockInstance>> worldIndex = candidatesByChunk.get(worldName);
        if (worldIndex == null) {
            return;
        }
        List<MultiblockInstance> owned = new ArrayList<>();
        for (Set<MultiblockInstance> bucket : worldIndex.values()) {
            for (MultiblockInstance instance : bucket) {
                if (instance.state() == MultiblockInstance.State.UNFORMED
                        && instance.pose().definitionId().equals(definitionId)
                        && owner.equals(instance.owner())
                        && !owned.contains(instance)) {
                    owned.add(instance);
                }
            }
        }
        while (owned.size() > limit) {
            MultiblockInstance oldest = null;
            for (MultiblockInstance instance : owned) {
                if (oldest == null || instance.ageNanos() > oldest.ageNanos()) {
                    oldest = instance;
                }
            }
            if (oldest == null) {
                return;
            }
            owned.remove(oldest);
            remove(oldest);
        }
    }

    private static List<Long> chunkKeysOf(StructurePose pose) {
        MultiblockDefinition definition = pose.definition();
        int sizeX = TemplateTransform.transformedSizeX(definition.sizeX(), definition.sizeZ(),
                pose.facing());
        int sizeZ = TemplateTransform.transformedSizeZ(definition.sizeX(), definition.sizeZ(),
                pose.facing());
        int minChunkX = (pose.anchorX()) >> 4;
        int maxChunkX = (pose.anchorX() + sizeX - 1) >> 4;
        int minChunkZ = (pose.anchorZ()) >> 4;
        int maxChunkZ = (pose.anchorZ() + sizeZ - 1) >> 4;
        List<Long> keys = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                keys.add(((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL));
            }
        }
        return keys;
    }

    public static long chunkKeyAt(int x, int z) {
        return ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
    }
}
