package emaki.jiuwu.craft.dungeon.world;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.audit.AuditService;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.InstanceState;
import emaki.jiuwu.craft.dungeon.instance.WorldCommandQueue;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceSlotAllocator;
import emaki.jiuwu.craft.dungeon.orchestration.PoolReservation;
import emaki.jiuwu.craft.dungeon.storage.InstancesStore;
import emaki.jiuwu.craft.dungeon.storage.InstancesStore.Entry;

public final class WorldPool {

    private static final long CREATE_RETRY_DELAY_SECONDS = 30L;

    private static final class PoolState {

        private final ConcurrentLinkedQueue<String> reserves = new ConcurrentLinkedQueue<>();
    }

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final File worldContainer;
    private final TemplateRepository templates;
    private final WorldNames names;
    private final WorldCloner cloner;
    private final WorldDisposer disposer;
    private final InstanceSlotAllocator allocator;
    private final InstanceRegistry registry;
    private final InstancesStore store;
    private final Function<String, Integer> poolMaxOf;
    private final Function<String, Integer> generationOf;
    private final MessageService messages;

    private final Map<String, PoolState> pools = new ConcurrentHashMap<>();
    private volatile boolean stopping;

    public WorldPool(Plugin plugin,
            ExecutionDispatcher dispatcher,
            File worldContainer,
            TemplateRepository templates,
            WorldNames names,
            WorldCloner cloner,
            WorldDisposer disposer,
            InstanceSlotAllocator allocator,
            InstanceRegistry registry,
            InstancesStore store,
            Function<String, Integer> poolMaxOf,
            Function<String, Integer> generationOf,
            MessageService messages) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.worldContainer = worldContainer;
        this.templates = templates;
        this.names = names;
        this.cloner = cloner;
        this.disposer = disposer;
        this.allocator = allocator;
        this.registry = registry;
        this.store = store;
        this.poolMaxOf = poolMaxOf;
        this.generationOf = generationOf;
        this.messages = messages;
    }

    public void stopNewWork() {
        stopping = true;
    }

    private volatile AuditService audit;

    public void bindAudit(AuditService audit) {
        this.audit = audit;
    }

    public void ensureReserve(String dungeonId) {
        if (stopping || dungeonId == null) {
            return;
        }
        PoolState state = pool(dungeonId);
        synchronized (state) {
            if (stopping) {
                return;
            }
            int active = registry.countActive(dungeonId);
            int target = Math.min(active + 1, poolMax(dungeonId));
            if (allocator.worldsOf(dungeonId) >= target) {
                return;
            }
            String worldName = names.next(dungeonId);
            PoolReservation reservation = allocator.tryReservePool(dungeonId, worldName);
            if (reservation == null) {
                return;
            }
            DungeonInstance instance = new DungeonInstance(worldName, dungeonId, newWorldQueue(worldName));
            if (!registry.register(instance)) {
                allocator.cancelPool(reservation);
                return;
            }
            store.put(worldName, new Entry(dungeonId, InstanceState.CREATING.name(), "", List.of(), System.currentTimeMillis()));
            store.saveAsync();
            if (audit != null) {
                audit.write("instance", Map.of(
                        "event", "created",
                        "instance", worldName,
                        "dungeon", dungeonId));
            }
            File template = templates.templateDir(dungeonId).orElse(null);
            if (template == null) {
                rollbackCreate(worldName, instance, reservation, false, "template missing");
                return;
            }
            int generation = generation(dungeonId);
            cloner.cloneAsync(template, worldName).whenComplete((ignored, error) -> {
                if (error != null) {
                    rollbackCreate(worldName, instance, reservation, true,
                            "clone failed: " + String.valueOf(error.getMessage()));
                    return;
                }
                dispatcher.runGlobal(plugin, () -> mount(worldName, dungeonId, instance, reservation, generation));
            });
        }
    }

    private void mount(String worldName, String dungeonId, DungeonInstance instance, PoolReservation reservation,
            int generation) {
        if (generation != generation(dungeonId)) {
            rollbackCreate(worldName, instance, reservation, true, "generation mismatch");
            return;
        }
        if (stopping || !instance.transition(InstanceState.CREATING, InstanceState.MOUNTING)) {
            rollbackCreate(worldName, instance, reservation, false, "state transition failed");
            return;
        }
        try {
            World world = Bukkit.createWorld(new WorldCreator(worldName));
            if (world == null) {
                throw new IllegalStateException("createWorld returned null");
            }
        } catch (Throwable throwable) {
            rollbackCreate(worldName, instance, reservation, true,
                    "createWorld failed: " + String.valueOf(throwable.getMessage()));
            return;
        }
        if (!instance.transition(InstanceState.MOUNTING, InstanceState.IDLE)) {
            rollbackCreate(worldName, instance, reservation, false, "state transition failed");
            return;
        }
        allocator.confirmPool(reservation);
        pool(dungeonId).reserves.add(worldName);
        store.put(worldName, new Entry(dungeonId, InstanceState.IDLE.name(), "", List.of(), System.currentTimeMillis()));
        store.saveAsync();
        dispatcher.runGlobal(plugin, () -> ensureReserve(dungeonId));
    }

    private void rollbackCreate(String worldName, DungeonInstance instance, PoolReservation reservation,
            boolean retry, String reason) {
        allocator.cancelPool(reservation);
        registry.unregister(worldName);
        store.remove(worldName);
        store.saveAsync();
        dispatcher.runAsync(plugin, () -> disposer.deleteResidual(worldName));
        messages.warning("console.world_create_failed", Map.of(
                "dungeon", instance.dungeonId(),
                "world", worldName,
                "error", reason
        ));
        if (retry && !stopping) {
            dispatcher.runAsyncLater(plugin, () -> ensureReserve(instance.dungeonId()),
                    CREATE_RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    public String takeReserve(String dungeonId) {
        PoolState state = pool(dungeonId);
        String worldName = state.reserves.poll();
        if (worldName == null) {
            return null;
        }
        DungeonInstance instance = registry.get(worldName);
        if (instance == null || !instance.transition(InstanceState.IDLE, InstanceState.RUNNING)) {
            invalidateReserve(dungeonId, worldName);
            return null;
        }
        ensureReserve(dungeonId);
        return worldName;
    }

    public void dropFromReserves(String dungeonId, String worldName) {
        pool(dungeonId).reserves.remove(worldName);
    }

    public List<String> reservesOf(String dungeonId) {
        return new ArrayList<>(pool(dungeonId).reserves);
    }

    public int reserveCount(String dungeonId) {
        return pool(dungeonId).reserves.size();
    }

    public void invalidateReserve(String dungeonId, String worldName) {
        PoolState state = pool(dungeonId);
        synchronized (state) {
            state.reserves.remove(worldName);
            DungeonInstance instance = registry.unregister(worldName);
            if (instance != null) {
                instance.closeLoop();
            }
            store.remove(worldName);
            store.saveAsync();
            allocator.releaseWorld(dungeonId);
            dispatcher.runAsync(plugin, () -> disposer.deleteResidual(worldName));
        }
        messages.warning("console.pool_reserve_invalidated", Map.of(
                "dungeon", dungeonId,
                "world", worldName
        ));
        dispatcher.runAsync(plugin, () -> ensureReserve(dungeonId));
    }

    public void invalidateAllReserves(String dungeonId) {
        if (dungeonId == null) {
            return;
        }
        for (String worldName : reservesOf(dungeonId)) {
            invalidateReserve(dungeonId, worldName);
        }
    }

    public boolean inspectReserves() {
        boolean healthy = true;
        for (Map.Entry<String, PoolState> pair : pools.entrySet()) {
            String dungeonId = pair.getKey();
            for (String worldName : pair.getValue().reserves) {
                World world = Bukkit.getWorld(worldName);
                File directory = WorldFolders.dataDir(worldContainer, worldName).toFile();
                boolean dataPresent = WorldFolders.anyPresent(worldContainer, worldName);
                if (world != null && dataPresent) {
                    continue;
                }
                if (world == null && dataPresent) {
                    try {
                        World remounted = Bukkit.createWorld(new WorldCreator(worldName));
                        if (remounted != null) {
                            if (audit != null) {
                                audit.write("instance", Map.of(
                                        "event", "reserve_remounted",
                                        "instance", worldName,
                                        "dungeon", dungeonId));
                            }
                            continue;
                        }
                    } catch (Throwable throwable) {
                        writeInspectFail(dungeonId, worldName, false,
                                directory.isDirectory(), String.valueOf(throwable.getMessage()));
                        invalidateReserve(dungeonId, worldName);
                        healthy = false;
                        continue;
                    }
                }
                writeInspectFail(dungeonId, worldName, false,
                        directory.isDirectory(), dataPresent ? "data-conflict" : "data-missing");
                invalidateReserve(dungeonId, worldName);
                healthy = false;
            }
        }
        return healthy;
    }

    private void writeInspectFail(String dungeonId, String worldName, boolean loaded,
            boolean legacyDirectory, String reason) {
        if (audit != null) {
            audit.write("instance", Map.of(
                    "event", "reserve_inspect_fail",
                    "instance", worldName,
                    "dungeon", dungeonId,
                    "loaded", String.valueOf(loaded),
                    "directory", String.valueOf(legacyDirectory),
                    "reason", reason));
        }
    }

    private WorldCommandQueue newWorldQueue(String worldName) {
        return new WorldCommandQueue(total -> messages.warning("console.world_command_deferred", Map.of(
                "world", worldName,
                "count", String.valueOf(total)
        )));
    }

    private PoolState pool(String dungeonId) {
        return pools.computeIfAbsent(dungeonId, key -> new PoolState());
    }

    private int poolMax(String dungeonId) {
        Integer poolMax = poolMaxOf.apply(dungeonId);
        return poolMax == null || poolMax < 1 ? 1 : poolMax;
    }

    private int generation(String dungeonId) {
        Integer generation = generationOf.apply(dungeonId);
        return generation == null || generation < 1 ? 1 : generation;
    }
}
