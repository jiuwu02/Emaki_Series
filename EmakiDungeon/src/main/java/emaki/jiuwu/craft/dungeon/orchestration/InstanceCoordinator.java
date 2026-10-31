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
import emaki.jiuwu.craft.dungeon.action.ActionBridge;
import emaki.jiuwu.craft.dungeon.audit.AuditService;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.InstanceState;
import emaki.jiuwu.craft.dungeon.score.ScoreTracker;
import emaki.jiuwu.craft.dungeon.script.ScriptRuntimeManager;
import emaki.jiuwu.craft.dungeon.storage.InstancesStore;
import emaki.jiuwu.craft.dungeon.trigger.TriggerType;
import emaki.jiuwu.craft.dungeon.world.WorldDisposer;
import emaki.jiuwu.craft.dungeon.world.WorldPool;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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
    private final Consumer<DungeonInstance> evacuator;
    private volatile ActionBridge actionBridge;
    private volatile ScriptRuntimeManager scriptManager;
    private volatile AuditService audit;
    private volatile ScoreTracker scoreTracker;
    private volatile Consumer<String> dropCleanup;

    private volatile boolean shuttingDown;
    private final Map<String, Long> disposingSince = new ConcurrentHashMap<>();
    private final Set<String> disposingAlerted = ConcurrentHashMap.newKeySet();
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
            Consumer<DungeonInstance> evacuator) {
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

    public void bindActionBridge(ActionBridge actionBridge) {
        this.actionBridge = actionBridge;
    }

    public void bindScoreTracker(ScoreTracker scoreTracker) {
        this.scoreTracker = scoreTracker;
    }

    public void bindDropCleanup(Consumer<String> dropCleanup) {
        this.dropCleanup = dropCleanup;
    }

    public void bindScriptManager(ScriptRuntimeManager scriptManager) {
        this.scriptManager = scriptManager;
    }

    public void bindAudit(AuditService audit) {
        this.audit = audit;
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
        long now = System.currentTimeMillis();
        for (DungeonInstance instance : registry.all()) {
            if (instance.state() == InstanceState.DISPOSING) {
                Long since = disposingSince.get(instance.instanceId());
                long seconds = since == null ? 0L : (now - since) / 1000L;
                if (since != null && seconds >= 60L
                        && disposingAlerted.add(instance.instanceId())) {
                    messages.warning("console.instance_disposing_stuck", Map.of(
                            "instance", instance.instanceId(),
                            "seconds", String.valueOf(seconds)
                    ));
                    if (audit != null) {
                        audit.write("instance", Map.of(
                                "event", "disposing_stuck",
                                "instance", instance.instanceId(),
                                "dungeon", instance.dungeonId(),
                                "seconds", String.valueOf(seconds)));
                    }
                }
            }
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
        disposingSince.put(instanceId, System.currentTimeMillis());
        debug.log("dungeon", (UUID) null, "instance_destroyed", Map.of(
                "instance", instanceId,
                "trigger", trigger
        ));
        if (audit != null) {
            audit.write("instance", Map.of(
                    "event", "destroyed",
                    "instance", instanceId,
                    "dungeon", instance.dungeonId(),
                    "outcome", trigger));
        }
        pool.dropFromReserves(instance.dungeonId(), instanceId);
        if (evacuator != null) {
            try {
                evacuator.accept(instance);
            } catch (RuntimeException ignored) {
            }
        }
        if (scriptManager != null) {
            if ("admin".equals(trigger) || "api_force_stop".equals(trigger)) {
                scriptManager.fireTriggerNow(
                        TriggerType.onAdminForceStop,
                        instanceId, instance.dungeonId(),
                        Map.of("instanceId", instanceId, "trigger", trigger));
            }
            scriptManager.fireTriggerNow(
                    TriggerType.onDestroy,
                    instanceId, instance.dungeonId(),
                    Map.of("instanceId", instanceId, "outcome", trigger));
        }
        instance.closeLoop();
        if (scriptManager != null) {
            scriptManager.closeSession(instance);
        }
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
            if (audit != null) {
                audit.write("instance", Map.of(
                        "event", held ? "held" : "disposed",
                        "instance", instanceId,
                        "dungeon", dungeonId));
            }
            finish(instance, held);
        });
    }

    private void finish(DungeonInstance instance, boolean held) {
        disposingSince.remove(instance.instanceId());
        disposingAlerted.remove(instance.instanceId());
        UUID partyId = instance.partyId();
        if (partyId != null) {
            allocator.releaseParty(partyId);
            instance.setPartyId(null);
        }
        String instanceId = instance.instanceId();
        registry.unregister(instanceId);
        if (scoreTracker != null) {
            scoreTracker.remove(instanceId);
        }
        if (dropCleanup != null) {
            try {
                dropCleanup.accept(instanceId);
            } catch (RuntimeException ignored) {
            }
        }
        store.remove(instanceId);
        if (held) {
            store.snapshot().addResidual(instanceId);
        }
        store.saveAsync();
        instance.transition(InstanceState.DISPOSING, InstanceState.DISPOSED);
        if (actionBridge != null) {
            actionBridge.dispatch("dungeon_destroy", null, Map.of(
                    "instanceId", instanceId,
                    "dungeonId", instance.dungeonId(),
                    "outcome", held ? "held" : "disposed"
            ));
        }
        pool.ensureReserve(instance.dungeonId());
    }

    public boolean shutdown() {
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
        return store.saveSync();
    }
}
