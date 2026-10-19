package emaki.jiuwu.craft.dungeon.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.InstanceState;
import emaki.jiuwu.craft.dungeon.storage.InstancesStore;
import emaki.jiuwu.craft.dungeon.world.WorldDisposer;
import emaki.jiuwu.craft.dungeon.world.WorldPool;

public final class InstanceCoordinator {

    private static final long INSPECT_PERIOD_TICKS = 600L;

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final WorldPool pool;
    private final WorldDisposer disposer;
    private final InstanceSlotAllocator allocator;
    private final InstanceRegistry registry;
    private final DestroyArbiter arbiter;
    private final InstancesStore store;
    private final MessageService messages;
    private final DebugLogger debug;
    private final java.util.function.Consumer<DungeonInstance> evacuator;

    private volatile boolean shuttingDown;
    private TaskToken inspectionTask = TaskToken.UNAVAILABLE;

    public InstanceCoordinator(Plugin plugin,
            ExecutionDispatcher dispatcher,
            WorldPool pool,
            WorldDisposer disposer,
            InstanceSlotAllocator allocator,
            InstanceRegistry registry,
            DestroyArbiter arbiter,
            InstancesStore store,
            MessageService messages,
            DebugLogger debug,
            java.util.function.Consumer<DungeonInstance> evacuator) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.pool = pool;
        this.disposer = disposer;
        this.allocator = allocator;
        this.registry = registry;
        this.arbiter = arbiter;
        this.store = store;
        this.messages = messages;
        this.debug = debug;
        this.evacuator = evacuator;
    }

    public void startupScan() {
        try {
            store.load();
        } catch (RuntimeException exception) {
            messages.warning("console.instances_load_failed", Map.of(
                    "error", String.valueOf(exception.getMessage())
            ));
            return;
        }
        List<String> worlds = new ArrayList<>(store.snapshot().instances().keySet());
        worlds.addAll(store.snapshot().residual());
        int cleaned = 0;
        for (String worldName : worlds) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                try {
                    Bukkit.unloadWorld(world, false);
                } catch (Throwable ignored) {
                }
            }
            if (disposer.deleteResidual(worldName)) {
                cleaned++;
            } else {
                messages.warning("console.residual_delete_failed", Map.of("world", worldName));
            }
        }
        store.snapshot().clear();
        store.saveSync();
        allocator.reset();
        if (cleaned > 0) {
            messages.info("console.residual_cleaned", Map.of("count", String.valueOf(cleaned)));
        }
    }

    public void warmupAll(List<String> dungeonIds) {
        for (String dungeonId : dungeonIds) {
            pool.ensureReserve(dungeonId);
        }
    }

    public void startInspection() {
        inspectionTask = dispatcher.runGlobalTimer(plugin, this::inspectNow, INSPECT_PERIOD_TICKS, INSPECT_PERIOD_TICKS);
    }

    private void inspectNow() {
        if (shuttingDown) {
            return;
        }
        pool.inspectReserves();
        for (DungeonInstance instance : registry.all()) {
            if (instance.state() == InstanceState.RUNNING && Bukkit.getWorld(instance.instanceId()) == null) {
                messages.warning("console.active_world_missing", Map.of(
                        "dungeon", instance.dungeonId(),
                        "world", instance.instanceId()
                ));
                dispose(instance.instanceId(), "external");
            }
        }
    }

    public void dispose(String instanceId, String trigger) {
        if (shuttingDown) {
            return;
        }
        DungeonInstance instance = registry.get(instanceId);
        if (instance == null) {
            return;
        }
        if (!arbiter.tryArbitrate(instanceId)) {
            return;
        }
        InstanceState current = instance.state();
        if (current == InstanceState.DISPOSING || current == InstanceState.DISPOSED) {
            return;
        }
        if (!instance.transition(current, InstanceState.DISPOSING)) {
            return;
        }
        debug.log("dungeon", (UUID) null, "instance_destroyed", Map.of(
                "instance", instanceId,
                "trigger", trigger
        ));
        pool.dropFromReserves(instance.dungeonId(), instanceId);
        if (evacuator != null) {
            try {
                evacuator.accept(instance);
            } catch (RuntimeException ignored) {
            }
        }
        instance.closeLoop();
        String dungeonId = instance.dungeonId();
        disposer.dispose(instanceId).whenComplete((result, error) -> {
            boolean held = error != null || result != WorldDisposer.Result.DISPOSED;
            if (held) {
                allocator.markHeld(dungeonId);
                messages.warning("console.world_dispose_held", Map.of(
                        "dungeon", dungeonId,
                        "world", instanceId
                ));
            } else {
                allocator.releaseWorld(dungeonId);
            }
            finish(instance, held);
        });
    }

    private void finish(DungeonInstance instance, boolean held) {
        UUID partyId = instance.partyId();
        if (partyId != null) {
            allocator.releaseParty(partyId);
            instance.setPartyId(null);
        }
        String instanceId = instance.instanceId();
        registry.unregister(instanceId);
        store.remove(instanceId);
        if (held) {
            store.snapshot().addResidual(instanceId);
        }
        store.saveAsync();
        instance.transition(InstanceState.DISPOSING, InstanceState.DISPOSED);
        pool.ensureReserve(instance.dungeonId());
    }

    public void shutdown() {
        shuttingDown = true;
        pool.stopNewWork();
        inspectionTask.cancel();
        for (DungeonInstance instance : registry.all()) {
            instance.closeLoop();
            World world = Bukkit.getWorld(instance.instanceId());
            if (world != null) {
                try {
                    Bukkit.unloadWorld(world, false);
                } catch (Throwable ignored) {
                }
            }
        }
        store.saveSync();
    }
}
