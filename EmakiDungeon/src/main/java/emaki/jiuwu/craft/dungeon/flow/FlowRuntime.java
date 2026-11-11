package emaki.jiuwu.craft.dungeon.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.gui.SoundParser;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.flow.ConditionEvaluator.EvalContext;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.EventIngress.IngressEvent;
import emaki.jiuwu.craft.dungeon.instance.WorldCommand;
import emaki.jiuwu.craft.dungeon.instance.WorldCommandTarget;
import emaki.jiuwu.craft.dungeon.script.TriggerSink;
import emaki.jiuwu.craft.dungeon.trigger.TriggerType;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;
import java.time.Duration;
import java.util.LinkedHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class FlowRuntime {

    final DungeonInstance instance;
    final FlowDefinition definition;
    final Adjudicator adjudicator;
    private final MessageService messages;
    private final Consumer<UUID> teleportPermit;
    final Plugin plugin;
    final ExecutionDispatcher dispatcher;
    volatile FlowControllers controllers;
    volatile PlayerStateHook stateHook;
    volatile TriggerSink scriptSink;
    volatile boolean scriptTakeover;
    volatile DebugLogger debug;

    final AtomicLong generation = new AtomicLong();
    final Set<TaskToken> scheduledTasks = ConcurrentHashMap.newKeySet();
    volatile boolean running = true;
    final Map<String, Object> facts = new ConcurrentHashMap<>();
    final Set<String> openedGates = ConcurrentHashMap.newKeySet();
    final Set<String> triggeredSpawns = ConcurrentHashMap.newKeySet();
    volatile int currentRoomOrder = Integer.MIN_VALUE;
    volatile String checkpointId = "";
    private static final int MAX_TIMELINE = 50;
    private final long createdAt = System.currentTimeMillis();
    private final List<String> timeline = new ArrayList<>();

    public FlowRuntime(DungeonInstance instance,
            FlowDefinition definition,
            Adjudicator adjudicator,
            MessageService messages,
            Consumer<UUID> teleportPermit,
            Plugin plugin,
            ExecutionDispatcher dispatcher) {
        this.instance = instance;
        this.definition = definition;
        this.adjudicator = adjudicator;
        this.messages = messages;
        this.teleportPermit = teleportPermit;
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        if (!definition.rooms().isEmpty()) {
            this.currentRoomOrder = definition.rooms().get(0).order() - 1;
        }
    }

    public void bindControllers(FlowControllers controllers) { new FlowRuntimeDelegate(this).bindControllers(controllers); }

    public void bindStateHook(PlayerStateHook stateHook) { new FlowRuntimeDelegate(this).bindStateHook(stateHook); }

    public void bindScript(TriggerSink scriptSink) { new FlowRuntimeDelegate(this).bindScript(scriptSink); }

    public void setScriptTakeover(boolean scriptTakeover) { new FlowRuntimeDelegate(this).setScriptTakeover(scriptTakeover); }

    public void bindDebug(DebugLogger debug) { new FlowRuntimeDelegate(this).bindDebug(debug); }

    void anchor(String message) { new FlowRuntimeDelegate(this).anchor(message); }

    public long instanceGeneration() { return new FlowRuntimeDelegate(this).instanceGeneration(); }

    public boolean isCurrentGeneration(long expected) { return new FlowRuntimeDelegate(this).isCurrentGeneration(expected); }

    public void shutdown() { new FlowRuntimeDelegate(this).shutdown(); }

    boolean schedule(Supplier<TaskToken> action, String description) { return new FlowRuntimeDelegate(this).schedule(action, description); }

    public boolean scheduleGlobal(long delayTicks, String description, Runnable task) { return new FlowRuntimeDelegate(this).scheduleGlobal(delayTicks, description, task); }

    public boolean isScriptTakeover() { return new FlowRuntimeDelegate(this).isScriptTakeover(); }

    void fireTrigger(TriggerType type, Map<String, String> context) { new FlowRuntimeDelegate(this).fireTrigger(type, context); }

    Map<String, String> playerCtx(UUID playerId, String key, String value) { return new FlowRuntimeDelegate(this).playerCtx(playerId, key, value); }

    public FlowDefinition definition() { return new FlowRuntimeDelegate(this).definition(); }

    public Adjudicator adjudicator() { return new FlowRuntimeDelegate(this).adjudicator(); }

    public DungeonInstance instance() { return new FlowRuntimeDelegate(this).instance(); }

    public void setFact(String key, Object value) { new FlowRuntimeDelegate(this).setFact(key, value); }

    public Object fact(String key) { return new FlowRuntimeDelegate(this).fact(key); }

    public Map<String, Object> factsView() { return new FlowRuntimeDelegate(this).factsView(); }

    public void recordFlowEvent(String kind, String detail) {
        long elapsed = (System.currentTimeMillis() - createdAt) / 1000L;
        String line = "+" + elapsed + "s [" + kind + "] " + detail;
        anchor("flow event | instance=" + instance.instanceId() + " | " + line);
        synchronized (timeline) {
            timeline.add(line);
            if (timeline.size() > MAX_TIMELINE) {
                timeline.remove(0);
            }
        }
    }

    public List<String> timelineView() {
        synchronized (timeline) {
            return List.copyOf(timeline);
        }
    }

    public int openedGateCount() { return new FlowRuntimeDelegate(this).openedGateCount(); }

    public int triggeredSpawnCount() { return new FlowRuntimeDelegate(this).triggeredSpawnCount(); }

    public int factInt(String key, int fallback) { return new FlowRuntimeDelegate(this).factInt(key, fallback); }

    public boolean factFlag(String key) { return new FlowRuntimeDelegate(this).factFlag(key); }

    public boolean isGateOpened(String gateId) { return new FlowRuntimeDelegate(this).isGateOpened(gateId); }

    public boolean hasTriggeredSpawn(String spawnId) { return new FlowRuntimeDelegate(this).hasTriggeredSpawn(spawnId); }

    public void markSpawnTriggered(String spawnId) { new FlowRuntimeDelegate(this).markSpawnTriggered(spawnId); }

    public int currentRoomOrder() { return new FlowRuntimeDelegate(this).currentRoomOrder(); }

    public String checkpointId() { return new FlowRuntimeDelegate(this).checkpointId(); }

    public void onEvent(IngressEvent event) {
        if (event == null) {
            return;
        }
        switch (event.trigger()) {
            case EventIngressKeys.ZONE -> onZone(event);
            case EventIngressKeys.RANGE -> onRange(event);
            case EventIngressKeys.BLOCK -> onBlockClick(event);
            case EventIngressKeys.ITEM -> onItemInteract(event);
            case EventIngressKeys.DEATH -> onEntityDeath(event);
            case EventIngressKeys.ACTIVATE -> onActivate(event);
            case EventIngressKeys.REDSTONE -> onRedstone(event);
            case EventIngressKeys.PLAYER_STATE -> {
                if (stateHook != null && "death".equals(event.payload().get("kind"))) {
                    stateHook.onPlayerDeath(instance, EventIngressKeys.player(event.payload()));
                }
            }
            case EventIngressKeys.CHEST -> onChestOpen(event);
            default -> {
            }
        }
    }

    void onChestOpen(IngressEvent event) { new FlowRuntimeDelegate(this).onChestOpen(event); }

    private void onZone(IngressEvent event) {
        String zoneId = event.sourceId();
        Map<String, Object> payload = event.payload();
        UUID playerId = EventIngressKeys.player(payload);
        anchor("zone event consumed | instance=" + instance.instanceId()
                + " | zone=" + zoneId
                + " | entered=" + payload.get(Facts.REGION_ENTERED)
                + " | left=" + payload.get(Facts.REGION_LEFT));
        EvalContext context = new EvalContext(playerId, payload);

        FlowModules.TriggerZoneDef triggerZone = definition.triggerZones().get(zoneId);
        if (triggerZone != null && triggerZone.head().enabled()) {
            boolean zoneAllowed =
                    adjudicator.adjudicate(triggerZone.head(), triggerZone.conditions(), context, playerId).allowed();
            if (zoneAllowed && Boolean.TRUE.equals(payload.get(Facts.REGION_ENTERED))) {
                fireTrigger(TriggerType.onRegionEnter,
                        playerCtx(playerId, "triggerId", zoneId));
            }
        }

        FlowModules.CheckpointDef checkpoint = definition.checkpoints().get(zoneId);
        if (checkpoint != null && checkpoint.head().enabled() && checkpoint.region() != null
                && payload.get(Facts.REGION_ENTERED) != null) {
            EvalContext checkpointContext = new EvalContext(playerId, payload);
            if (adjudicator.adjudicate(checkpoint.head(), List.of(), checkpointContext, playerId).allowed()) {
                checkpointId = checkpoint.recordId();
                recordFlowEvent("checkpoint", "id=" + checkpoint.recordId());
                fireTrigger(TriggerType.onCheckpoint,
                        playerCtx(playerId, "checkpointId", checkpoint.recordId()));
            }
        }

        FlowModules.TeleportZoneDef teleportZone = definition.teleportZones().get(zoneId);
        if (teleportZone != null && teleportZone.head().enabled()
                && Boolean.TRUE.equals(payload.get(Facts.REGION_ENTERED))) {
            EvalContext zoneContext = new EvalContext(playerId, payload);
            if (adjudicator.adjudicate(teleportZone.head(), teleportZone.conditions(), zoneContext, playerId).allowed()) {
                submitTeleport(playerId, teleportZone.target());
            }
        }

        FlowModules.BoundaryZoneDef boundaryZone = definition.boundaryZones().get(zoneId);
        if (boundaryZone != null && boundaryZone.head().enabled()
                && Boolean.TRUE.equals(payload.get(Facts.REGION_LEFT))) {
            submitTeleport(playerId, boundaryZone.pullback() != null
                    ? boundaryZone.pullback()
                    : boundaryZone.region().center());
        }

        FlowModules.RoomTimerDef roomTimer = definition.roomTimers().get(zoneId);
        if (roomTimer != null && roomTimer.head().enabled()
                && Boolean.TRUE.equals(payload.get(Facts.REGION_ENTERED))) {
            EvalContext timerContext = new EvalContext(playerId, payload);
            var verdict = adjudicator.adjudicate(roomTimer.head(), List.of(), timerContext, playerId);
            anchor("room timer adjudicated | instance=" + instance.instanceId()
                    + " | zone=" + zoneId + " | allowed=" + verdict.allowed()
                    + " | exhausted=" + verdict.exhaustedTier()
                    + " | conditionFailed=" + verdict.conditionFailed());
            if (verdict.allowed()) {
                scheduleRoomTimer(roomTimer, playerId);
            }
        }

        handleRoomZone(zoneId, playerId, payload);

        FlowModules.EffectZoneDef effectZone = definition.effectZones().get(zoneId);
        if (effectZone != null && effectZone.head().enabled()
                && Boolean.TRUE.equals(payload.get("effect_tick"))) {
            if (adjudicator.adjudicate(effectZone.head(), List.of(), context, playerId).allowed()) {
                applyEffects(effectZone, playerId);
            }
        }

        FlowModules.OptionDef option = definition.options().get(zoneId);
        if (option != null && option.head().enabled()
                && Boolean.TRUE.equals(payload.get(Facts.REGION_ENTERED))) {
            EvalContext optionContext = new EvalContext(playerId, payload);
            if (adjudicator.adjudicate(option.head(), option.conditions(), optionContext, playerId).allowed()
                    && controllers != null && playerId != null) {
                controllers.handleOptionTrigger(this, option.optionId(), playerId);
            }
        }

        FlowModules.ExitPointDef exitPoint = definition.exitPoints().get(zoneId);
        if (exitPoint != null && exitPoint.head().enabled()
                && Boolean.TRUE.equals(payload.get(Facts.REGION_ENTERED)) && playerId != null) {
            EvalContext exitContext = new EvalContext(playerId, payload);
            if (adjudicator.adjudicate(exitPoint.head(), List.of(), exitContext, playerId).allowed()
                    && controllers != null) {
                anchor("exit point triggered | instance=" + instance.instanceId()
                        + " | exit=" + exitPoint.head().id());
                recordFlowEvent("exit_point", "exit=" + exitPoint.head().id());
                // 退出链路会恢复玩家位置并投递 Bukkit 事件, 必须回到主线程执行,
                // 否则会把实例虚拟线程上的事件派发变成异步调用而被拒绝
                dispatcher.runGlobal(plugin, () -> {
                    FlowControllers bound = controllers;
                    if (bound == null) {
                        return;
                    }
                    org.bukkit.entity.Player player = plugin.getServer().getPlayer(playerId);
                    if (player == null || !player.isOnline()) {
                        return;
                    }
                    bound.handleExitPoint(this, exitPoint, playerId);
                });
            }
        }
    }

    private void handleRoomZone(String roomId, UUID playerId, Map<String, Object> payload) { new FlowRuntimeDelegate(this).handleRoomZone(roomId, playerId, payload); }

    public String currentRoomId() { return new FlowRuntimeDelegate(this).currentRoomId(); }

    void notifyRoomEntered(String roomId, UUID playerId) { new FlowRuntimeDelegate(this).notifyRoomEntered(roomId, playerId); }

    public void scriptEnterRoom(String roomId) { new FlowRuntimeDelegate(this).scriptEnterRoom(roomId); }

    public void scriptHint(String text, String channel) { new FlowRuntimeDelegate(this).scriptHint(text, channel); }

    void syncStragglers(String previousRoomId, UUID arrivingPlayer) {
        if (previousRoomId.isEmpty() || arrivingPlayer == null) {
            return;
        }
        FlowModules.RoomDef previousRoom = null;
        for (FlowModules.RoomDef room : definition.rooms()) {
            if (room.id().equals(previousRoomId)) {
                previousRoom = room;
                break;
            }
        }
        if (previousRoom == null || previousRoom.region() == null) {
            return;
        }
        Player arriving = Bukkit.getPlayer(arrivingPlayer);
        if (arriving == null) {
            return;
        }
        Location target = arriving.getLocation().clone();
        FlowRegion region = previousRoom.region();
        instance.commands().submit(new WorldCommand() {
            @Override
            public int blockCost() {
                return 0;
            }

            @Override
            public WorldCommandTarget target() {
                return WorldCommandTarget.GLOBAL;
            }

            @Override
            public void execute() {
                World world = target.getWorld();
                if (world == null) {
                    return;
                }
                List<UUID> stragglers = new ArrayList<>();
                for (Player member : world.getPlayers()) {
                    if (member.getUniqueId().equals(arrivingPlayer)) {
                        continue;
                    }
                    Location location = member.getLocation();
                    if (region.contains(location.getX(), location.getY(), location.getZ())) {
                        stragglers.add(member.getUniqueId());
                    }
                }
                for (UUID straggler : stragglers) {
                    submitTeleportTo(straggler, target);
                }
            }
        });
    }

    private void submitTeleportTo(UUID playerId, Location destination) {
        Location target = destination.clone();
        instance.commands().submit(new WorldCommand() {
            @Override
            public int blockCost() {
                return 0;
            }

            @Override
            public WorldCommandTarget target() {
                return WorldCommandTarget.ENTITY;
            }

            @Override
            public UUID entityId() {
                return playerId;
            }

            @Override
            public void execute() {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    return;
                }
                teleportPermit.accept(playerId);
                player.teleportAsync(target);
            }
        });
    }

    private void onRange(IngressEvent event) { new FlowRuntimeDelegate(this).onRange(event); }

    private void onBlockClick(IngressEvent event) { new FlowRuntimeDelegate(this).onBlockClick(event); }

    private void onItemInteract(IngressEvent event) { new FlowRuntimeDelegate(this).onItemInteract(event); }

    private void onEntityDeath(IngressEvent event) { new FlowRuntimeDelegate(this).onEntityDeath(event); }

    void evaluateKillTargets(String mobId, UUID playerId) { new FlowRuntimeDelegate(this).evaluateKillTargets(mobId, playerId); }

    private void onActivate(IngressEvent event) { new FlowRuntimeDelegate(this).onActivate(event); }

    private void onRedstone(IngressEvent event) { new FlowRuntimeDelegate(this).onRedstone(event); }

    public void evaluateGates(UUID playerId) { new FlowRuntimeDelegate(this).evaluateGates(playerId); }

    private void scheduleRoomTimer(FlowModules.RoomTimerDef roomTimer, UUID playerId) {
        String key = "room_timer:" + roomTimer.head().id();
        if (factFlag(key)) {
            anchor("room timer already scheduled | instance=" + instance.instanceId()
                    + " | timer=" + roomTimer.head().id());
            return;
        }
        long expected = generation.get();
        long deadline = System.currentTimeMillis() + roomTimer.seconds() * 1000L;
        boolean scheduled = schedule(() -> dispatcher.runGlobalLater(plugin,
                () -> fireRoomTimer(roomTimer, playerId, expected), roomTimer.seconds() * 20L),
                "房间倒计时 " + roomTimer.head().id());
        if (!scheduled) {
            anchor("room timer aborted | instance=" + instance.instanceId()
                    + " | timer=" + roomTimer.head().id() + " | reason=调度被拒绝");
            return;
        }
        setFact(key, true);
        setFact(key + ":deadline", deadline);
        anchor("room timer scheduled | instance=" + instance.instanceId()
                + " | timer=" + roomTimer.head().id() + " | seconds=" + roomTimer.seconds());
    }

    private void fireRoomTimer(FlowModules.RoomTimerDef roomTimer, UUID playerId, long expected) {
        if (!isCurrentGeneration(expected)) {
            anchor("room timer discarded | instance=" + instance.instanceId()
                    + " | timer=" + roomTimer.head().id() + " | reason=实例代际已失效");
            return;
        }
        anchor("room timer fired | instance=" + instance.instanceId()
                + " | timer=" + roomTimer.head().id());
        recordFlowEvent("room_timer", "timer=" + roomTimer.head().id());
        if (instance.commands() == null) {
            anchor("room timer aborted | instance=" + instance.instanceId()
                    + " | reason=commands_null");
            return;
        }
        setFact(Facts.timerMark(roomTimer.head().id()), true);
        setFact(Facts.TIMER_REMAINING, 0);
        executeActions(List.of(roomTimer.onZero()),
                new EvalContext(playerId, Map.of()), playerId);
    }

    public void executeActions(List<FlowActionDef> actions, EvalContext context, UUID playerId) { new FlowRuntimeDelegate(this).executeActions(actions, context, playerId); }

    void executeAction(FlowActionDef action, EvalContext context, UUID playerId) {
        switch (action.type()) {
            case "hint" -> submitHint(playerId, action.param("text", ""), action.param("channel", "chat"));
            case "message" -> submitMessage(playerId, action.param("key", ""), action.param("channel", "chat"));
            case "command" -> submitConsoleCommand(replacePlayer(action.param("command", ""), playerId));
            case "teleport" -> submitTeleport(playerId, new FlowPoint(
                    action.paramDouble("x", 0.0D),
                    action.paramDouble("y", 64.0D),
                    action.paramDouble("z", 0.0D),
                    (float) action.paramDouble("yaw", 0.0D),
                    (float) action.paramDouble("pitch", 0.0D)));
            case "fx" -> submitFx(action);
            case "set_block" -> {
                FlowPoint point = new FlowPoint(
                        action.paramDouble("x", 0.0D),
                        action.paramDouble("y", 64.0D),
                        action.paramDouble("z", 0.0D),
                        0.0F, 0.0F);
                submitSetBlock(playerId, new FlowModules.BlockStateTarget(point, action.param("block_data", "")));
            }
            case "open_gate" -> {
                if (controllers != null) {
                    controllers.openGate(this, action.param("gate", ""), playerId);
                }
            }
            case "spawn_wave" -> {
                if (controllers != null) {
                    controllers.spawnWave(this, action.param("spawn", ""),
                            action.paramInt("wave", 0), playerId);
                }
            }
            case "fail" -> {
                recordFlowEvent("fail", "reason=" + action.param("reason", "scripted"));
                if (controllers != null) {
                    controllers.triggerFail(this, action.param("reason", "scripted"));
                }
            }
            case "complete" -> {
                anchor("complete action | instance=" + instance.instanceId()
                        + " | cause=" + action.param("cause", "flow")
                        + " | controllers=" + (controllers != null));
                recordFlowEvent("complete", "cause=" + action.param("cause", "flow"));
                if (controllers != null) {
                    controllers.triggerComplete(this, action.param("cause", "flow"));
                }
            }
            case "give_temp_item" -> {
                if (controllers != null) {
                    controllers.giveTempItem(this, action.param("item", ""), action.paramInt("amount", 1));
                }
            }
            default -> {
            }
        }
    }

    private void applyEffects(FlowModules.EffectZoneDef effectZone, UUID playerId) {
        if (playerId == null || effectZone.effects().isEmpty()) {
            return;
        }
        List<String> effects = List.copyOf(effectZone.effects());
        instance.commands().submit(new WorldCommand() {
            @Override
            public int blockCost() {
                return 0;
            }

            @Override
            public WorldCommandTarget target() {
                return WorldCommandTarget.ENTITY;
            }

            @Override
            public UUID entityId() {
                return playerId;
            }

            @Override
            public void execute() {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    return;
                }
                applyEffectSpecs(player, effects);
            }
        });
    }

    private void applyEffectSpecs(Player player, List<String> effects) { new FlowRuntimeDelegate(this).applyEffectSpecs(player, effects); }


    void submitHint(UUID playerId, String text, String channel) {
        List<UUID> audience = audienceOf(playerId);
        if (audience.isEmpty()) {
            return;
        }
        Component component = MiniMessages.parse(text);
        for (UUID memberId : audience) {
            instance.commands().submit(new WorldCommand() {
                @Override
                public int blockCost() {
                    return 0;
                }

                @Override
                public WorldCommandTarget target() {
                    return WorldCommandTarget.ENTITY;
                }

                @Override
                public UUID entityId() {
                    return memberId;
                }

                @Override
                public void execute() {
                    Player player = Bukkit.getPlayer(memberId);
                    if (player == null) {
                        return;
                    }
                    showHint(player, component, channel);
                }
            });
        }
    }

    private void showHint(Player player, Component component, String channel) { new FlowRuntimeDelegate(this).showHint(player, component, channel); }

    private void submitMessage(UUID playerId, String key, String channel) {
        if (key.isEmpty()) {
            return;
        }
        List<UUID> audience = audienceOf(playerId);
        if (audience.isEmpty()) {
            return;
        }
        for (UUID memberId : audience) {
            instance.commands().submit(new WorldCommand() {
                @Override
                public int blockCost() {
                    return 0;
                }

                @Override
                public WorldCommandTarget target() {
                    return WorldCommandTarget.ENTITY;
                }

                @Override
                public UUID entityId() {
                    return memberId;
                }

                @Override
                public void execute() {
                    Player player = Bukkit.getPlayer(memberId);
                    if (player == null) {
                        return;
                    }
                    messages.send(player, key, Map.of());
                }
            });
        }
    }

    private List<UUID> audienceOf(UUID playerId) { return new FlowRuntimeDelegate(this).audienceOf(playerId); }

    private void submitConsoleCommand(String commandLine) {
        if (commandLine.isBlank()) {
            return;
        }
        instance.commands().submit(new WorldCommand() {
            @Override
            public int blockCost() {
                return 0;
            }

            @Override
            public WorldCommandTarget target() {
                return WorldCommandTarget.GLOBAL;
            }

            @Override
            public void execute() {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandLine);
            }
        });
    }

    private void submitTeleport(UUID playerId, FlowPoint point) {
        if (playerId == null || point == null) {
            return;
        }
        instance.commands().submit(new WorldCommand() {
            @Override
            public int blockCost() {
                return 0;
            }

            @Override
            public WorldCommandTarget target() {
                return WorldCommandTarget.ENTITY;
            }

            @Override
            public UUID entityId() {
                return playerId;
            }

            @Override
            public void execute() {
                Player player = Bukkit.getPlayer(playerId);
                World world = Bukkit.getWorld(instance.instanceId());
                if (player == null || world == null) {
                    return;
                }
                teleportPermit.accept(playerId);
                player.teleportAsync(new Location(world, point.x(), point.y(), point.z(),
                        point.yaw(), point.pitch()));
            }
        });
    }

    private void submitFx(FlowActionDef action) {
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
                World world = Bukkit.getWorld(instance.instanceId());
                if (world == null) {
                    return null;
                }
                Location spawn = world.getSpawnLocation();
                return new double[] {spawn.getX(), spawn.getY(), spawn.getZ()};
            }

            @Override
            public void execute() {
                World world = Bukkit.getWorld(instance.instanceId());
                if (world == null) {
                    return;
                }
                String sound = action.param("sound", "");
                if (!sound.isBlank()) {
                    Sound resolved = SoundParser.resolve(sound);
                    if (resolved != null) {
                        world.playSound(world.getSpawnLocation(), resolved, 1.0F, 1.0F);
                    }
                }
                String particle = action.param("particle", "");
                if (!particle.isBlank()) {
                    try {
                        Particle type = Particle.valueOf(particle.toUpperCase(Locale.ROOT));
                        Location spawn = world.getSpawnLocation();
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                "execute in " + world.getKey() + " run particle " + type.getKey()
                                        + " " + spawn.getX() + " " + spawn.getY() + " " + spawn.getZ()
                                        + " 0 0 0 0 20 force");
                    } catch (IllegalArgumentException ignored) {
                        FailureRecorder.recordIgnoredFailure("EmakiDungeon", "FlowRuntime.java#2", ignored);
                    }
                }
            }
        });
    }

    void submitSetBlock(UUID playerId, FlowModules.BlockStateTarget target) {
        if (target == null || target.blockData().isBlank()) {
            return;
        }
        FlowPoint point = target.point();
        instance.commands().submit(new WorldCommand() {
            @Override
            public int blockCost() {
                return 1;
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
                World world = Bukkit.getWorld(instance.instanceId());
                if (world == null) {
                    return;
                }
                var blockData = Bukkit.createBlockData(target.blockData());
                world.getBlockAt((int) Math.floor(point.x()),
                        (int) Math.floor(point.y()),
                        (int) Math.floor(point.z())).setBlockData(blockData, false);
            }
        });
    }

    private String replacePlayer(String commandLine, UUID playerId) { return new FlowRuntimeDelegate(this).replacePlayer(commandLine, playerId); }
}
