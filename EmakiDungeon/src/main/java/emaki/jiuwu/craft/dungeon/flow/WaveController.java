package emaki.jiuwu.craft.dungeon.flow;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.InstanceState;
import emaki.jiuwu.craft.dungeon.instance.WorldCommand;
import emaki.jiuwu.craft.dungeon.instance.WorldCommandTarget;
import emaki.jiuwu.craft.dungeon.script.ScriptRuntimeManager;
import emaki.jiuwu.craft.dungeon.trigger.TriggerType;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.attribute.Attribute;

public final class WaveController {

    private static final String META_NO_DROP = "emd_no_drop";

    private record MobOrigin(String spawnId, int waveIndex, String bossId) {
    }

    private static final class WaveRuntime {

        private final AtomicInteger alive = new AtomicInteger();
        private final Set<UUID> living = ConcurrentHashMap.newKeySet();
        private volatile int currentWave;
    }

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final DungeonInstance instance;
    private final MobSource mobSource;
    private final DebugLogger debug;
    private final Function<String, DungeonConfig> configOf;

    private final Map<String, WaveRuntime> waves = new ConcurrentHashMap<>();
    private final Map<UUID, MobOrigin> origins = new ConcurrentHashMap<>();
    private final Set<String> bossesSpawned = ConcurrentHashMap.newKeySet();

    private volatile BiConsumer<String, Integer> waveStartListener;
    private volatile BiConsumer<String, String> gateOpenListener;
    private volatile Consumer<String> roomListener;
    private volatile ScriptRuntimeManager scriptTrigger;

    public void bindTriggerSink(ScriptRuntimeManager scriptTrigger) {
        this.scriptTrigger = scriptTrigger;
    }

    private void fireTrigger(TriggerType type,
            Map<String, String> context) {
        if (scriptTrigger != null) {
            scriptTrigger.fireTrigger(type, instance.instanceId(), instance.dungeonId(), context);
        }
    }

    public WaveController(Plugin plugin,
            ExecutionDispatcher dispatcher,
            DungeonInstance instance,
            MobSource mobSource,
            DebugLogger debug,
            Function<String, DungeonConfig> configOf) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.instance = instance;
        this.mobSource = mobSource;
        this.debug = debug;
        this.configOf = configOf;
    }

    private double mobMultiplier() {
        if (configOf == null) {
            return 1.0D;
        }
        DungeonConfig config = configOf.apply(instance.dungeonId());
        return config == null ? 1.0D : config.mobMultiplier(instance.difficulty());
    }

    private void applyDifficultyTuning(LivingEntity entity) {
        double multiplier = mobMultiplier();
        if (multiplier == 1.0D || entity == null) {
            return;
        }
        var maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            double boosted = maxHealth.getValue() * multiplier;
            maxHealth.setBaseValue(boosted);
            entity.setHealth(Math.min(boosted, entity.getHealth() * multiplier));
        }
        var attackDamage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.setBaseValue(attackDamage.getValue() * multiplier);
        }
    }

    public void bindListeners(BiConsumer<String, Integer> waveStartListener,
            BiConsumer<String, String> gateOpenListener,
            Consumer<String> roomListener) {
        this.waveStartListener = waveStartListener;
        this.gateOpenListener = gateOpenListener;
        this.roomListener = roomListener;
    }

    public boolean spawnWave(FlowRuntime runtime, String spawnId, int waveIndex) {
        FlowModules.SpawnPointModuleDef spawnPoint = runtime.definition().spawnPoints().get(spawnId);
        if (spawnPoint == null || waveIndex < 0 || waveIndex >= spawnPoint.waves().size()) {
            return false;
        }
        FlowModules.WaveDef wave = spawnPoint.waves().get(waveIndex);
        WaveRuntime waveRuntime = waves.computeIfAbsent(spawnId, key -> new WaveRuntime());
        waveRuntime.currentWave = waveIndex;
        if (waveStartListener != null) {
            waveStartListener.accept(spawnId, waveIndex);
        }
        debug.logRaw("dungeon", (UUID) null, "wave start | spawn=" + spawnId + " | wave=" + waveIndex);
        int order = 0;
        for (FlowModules.MobSpawnRef ref : wave.mobs()) {
            for (int i = 0; i < ref.count(); i++) {
                int delayTicks = Math.max(0, order) * Math.max(1, wave.intervalTicks());
                scheduleSpawn(runtime, spawnId, waveIndex, wave.dropLoot(),
                        spawnPoint.point(), ref.mobId(), delayTicks, waveRuntime);
                order++;
            }
        }
        return true;
    }

    private void scheduleSpawn(FlowRuntime runtime, String spawnId, int waveIndex, boolean dropLoot,
            FlowPoint point, String mobId, int delayTicks, WaveRuntime waveRuntime) {
        boolean scheduled = runtime.scheduleGlobal(delayTicks, "波次生成 " + spawnId, () -> {
            if (instance.state() != InstanceState.RUNNING) {
                return;
            }
            instance.commands().submit(new WorldCommand() {
                @Override
                public int blockCost() {
                    return 0;
                }

                @Override
                public WorldCommandTarget target() {
                    return WorldCommandTarget.LOCATION;
                }

                @Override
                public String worldName() {
                    return instance.instanceId();
                }

                @Override
                public double[] anchor() {
                    return new double[] {point.x(), point.y(), point.z()};
                }

                @Override
                public void execute() {
                    MobSource.SpawnResult result = mobSource.spawn(instance.instanceId(), point, mobId, 1);
                    if (!result.success()) {
                        return;
                    }
                    for (LivingEntity entity : result.spawned()) {
                        applyDifficultyTuning(entity);
                        if (!dropLoot) {
                            entity.setMetadata(META_NO_DROP,
                                    new org.bukkit.metadata.FixedMetadataValue(plugin, Boolean.TRUE));
                        }
                        waveRuntime.living.add(entity.getUniqueId());
                        waveRuntime.alive.incrementAndGet();
                        origins.put(entity.getUniqueId(), new MobOrigin(spawnId, waveIndex, null));
                    }
                }
            });
        });
        if (!scheduled) {
            debug.logRaw("dungeon", (UUID) null,
                    "wave spawn aborted | spawn=" + spawnId + " | reason=调度被拒绝");
        }
    }

    public void onMobDeath(UUID entityId) {
        if (entityId == null) {
            return;
        }
        MobOrigin origin = origins.remove(entityId);
        if (origin == null) {
            return;
        }
        FlowRuntime runtime = instance.flowRuntime();
        if (origin.bossId() != null && !origin.bossId().isEmpty() && runtime != null) {
            runtime.setFact(Facts.bossKilled(origin.bossId()), true);
            debug.logRaw("dungeon", (UUID) null, "boss killed | boss=" + origin.bossId());
            fireTrigger(TriggerType.onBossKill,
                    Map.of("bossId", origin.bossId()));
        }
        WaveRuntime waveRuntime = waves.get(origin.spawnId());
        if (waveRuntime == null) {
            return;
        }
        waveRuntime.living.remove(entityId);
        if (waveRuntime.alive.decrementAndGet() > 0) {
            return;
        }
        if (waveRuntime.currentWave != origin.waveIndex()) {
            return;
        }
        if (runtime == null) {
            return;
        }
        FlowModules.SpawnPointModuleDef spawnPoint = runtime.definition().spawnPoints().get(origin.spawnId());
        if (spawnPoint == null) {
            return;
        }
        int nextWave = origin.waveIndex() + 1;
        if (nextWave < spawnPoint.waves().size()) {
            spawnWave(runtime, origin.spawnId(), nextWave);
            return;
        }
        runtime.setFact(Facts.waveCleared(origin.spawnId()), true);
        debug.logRaw("dungeon", (UUID) null, "wave clear | spawn=" + origin.spawnId());
        fireTrigger(TriggerType.onWaveClear, Map.of(
                "spawnId", origin.spawnId(),
                "wave", String.valueOf(origin.waveIndex())));
    }

    public void syncBossPoints(FlowRuntime runtime) {
        ConditionEvaluator.EvalContext context = new ConditionEvaluator.EvalContext(null, runtime.factsView());
        for (FlowModules.BossPointDef bossPoint : runtime.definition().bossPoints().values()) {
            String bossKey = bossPoint.head().id();
            if (!bossPoint.head().enabled() || bossesSpawned.contains(bossKey)) {
                continue;
            }
            if (!runtime.adjudicator().evaluator().evaluateAll(bossPoint.conditions(), "AND", context)) {
                continue;
            }
            if (runtime.adjudicator().counter().check(bossPoint.head(), null)
                    != TriggerCounter.ExhaustedTier.NONE) {
                continue;
            }
            if (!runtime.adjudicator().counter().consume(bossPoint.head(), null)) {
                continue;
            }
            bossesSpawned.add(bossKey);
            spawnBoss(runtime, bossPoint);
        }
    }

    private void spawnBoss(FlowRuntime runtime, FlowModules.BossPointDef bossPoint) {
        dispatcher.runGlobal(plugin, () -> {
            if (instance.state() != InstanceState.RUNNING) {
                return;
            }
            instance.commands().submit(new WorldCommand() {
                @Override
                public int blockCost() {
                    return 0;
                }

                @Override
                public WorldCommandTarget target() {
                    return WorldCommandTarget.LOCATION;
                }

                @Override
                public String worldName() {
                    return instance.instanceId();
                }

                @Override
                public double[] anchor() {
                    FlowPoint point = bossPoint.point();
                    return new double[] {point.x(), point.y(), point.z()};
                }

                @Override
                public void execute() {
                    String source = bossPoint.source() == null || bossPoint.source().isBlank()
                            ? "vanilla"
                            : bossPoint.source();
                    String mobId = source + ":" + bossPoint.bossId();
                    if ("vanilla".equals(source)) {
                        mobId = bossPoint.bossId();
                    }
                    MobSource.SpawnResult result =
                            mobSource.spawn(instance.instanceId(), bossPoint.point(), mobId, 1);
                    for (LivingEntity entity : result.spawned()) {
                        applyDifficultyTuning(entity);
                        origins.put(entity.getUniqueId(), new MobOrigin("", -1, bossPoint.bossId()));
                    }
                    debug.logRaw("dungeon", (UUID) null,
                            "boss spawned | boss=" + bossPoint.bossId());
                    fireTrigger(TriggerType.onBossSpawn,
                            Map.of("bossId", bossPoint.bossId()));
                }
            });
        });
    }

    public static boolean hasNoDrop(LivingEntity entity) {
        return entity != null && entity.hasMetadata(META_NO_DROP);
    }

    public void notifyRoomEntered(String roomId) {
        if (roomListener != null) {
            roomListener.accept(roomId);
        }
    }

    public void notifyGateOpened(String gateId, String condition) {
        if (gateOpenListener != null) {
            gateOpenListener.accept(gateId, condition);
        }
    }

    public void clear() {
        waves.clear();
        origins.clear();
        bossesSpawned.clear();
    }
}
