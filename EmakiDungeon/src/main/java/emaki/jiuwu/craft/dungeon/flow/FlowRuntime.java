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
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.flow.ConditionEvaluator.EvalContext;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.EventIngress.IngressEvent;
import emaki.jiuwu.craft.dungeon.instance.WorldCommand;
import emaki.jiuwu.craft.dungeon.instance.WorldCommandTarget;
import emaki.jiuwu.craft.dungeon.script.TriggerSink;
import emaki.jiuwu.craft.dungeon.trigger.TriggerType;
import java.time.Duration;
import java.util.LinkedHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class FlowRuntime {

    private final DungeonInstance instance;
    private final FlowDefinition definition;
    private final Adjudicator adjudicator;
    private final MessageService messages;
    private final Consumer<UUID> teleportPermit;
    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private volatile FlowControllers controllers;
    private volatile PlayerStateHook stateHook;
    private volatile TriggerSink scriptSink;
    private volatile boolean scriptTakeover;
    private volatile DebugLogger debug;

    private final AtomicLong generation = new AtomicLong();
    private final Set<TaskToken> scheduledTasks = ConcurrentHashMap.newKeySet();
    private volatile boolean running = true;
    private final Map<String, Object> facts = new ConcurrentHashMap<>();
    private final Set<String> openedGates = ConcurrentHashMap.newKeySet();
    private final Set<String> triggeredSpawns = ConcurrentHashMap.newKeySet();
    private volatile int currentRoomOrder = Integer.MIN_VALUE;
    private volatile String checkpointId = "";

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

    public void bindControllers(FlowControllers controllers) {
        this.controllers = controllers;
    }

    public void bindStateHook(PlayerStateHook stateHook) {
        this.stateHook = stateHook;
    }

    public void bindScript(TriggerSink scriptSink) {
        this.scriptSink = scriptSink;
    }

    public void setScriptTakeover(boolean scriptTakeover) {
        this.scriptTakeover = scriptTakeover;
    }

    public void bindDebug(DebugLogger debug) {
        this.debug = debug;
    }

    private void anchor(String message) {
        DebugLogger logger = debug;
        if (logger != null) {
            logger.logRaw("dungeon", (UUID) null, message);
        }
    }

    public long instanceGeneration() {
        return generation.get();
    }

    public boolean isCurrentGeneration(long expected) {
        return running && generation.get() == expected;
    }

    public void shutdown() {
        running = false;
        generation.incrementAndGet();
        for (TaskToken token : scheduledTasks) {
            if (!token.cancelled()) {
                token.cancel();
            }
        }
        scheduledTasks.clear();
    }

    private boolean schedule(Supplier<TaskToken> action, String description) {
        if (!running) {
            return false;
        }
        TaskToken token;
        try {
            token = action.get();
        } catch (RuntimeException exception) {
            DebugLogger logger = debug;
            if (logger != null) {
                logger.logRaw("dungeon", (UUID) null, "调度失败 | instance=" + instance.instanceId()
                        + " | task=" + description + " | error=" + exception.getMessage());
            }
            return false;
        }
        if (token == null || token.cancelled()) {
            return false;
        }
        scheduledTasks.add(token);
        return true;
    }

    public boolean scheduleGlobal(long delayTicks, String description, Runnable task) {
        long expected = generation.get();
        return schedule(() -> dispatcher.runGlobalLater(plugin, () -> {
            if (!isCurrentGeneration(expected)) {
                anchor("scheduled task discarded | instance=" + instance.instanceId()
                        + " | task=" + description + " | reason=实例代际已失效");
                return;
            }
            task.run();
        }, delayTicks), description);
    }

    public boolean isScriptTakeover() {
        return scriptTakeover;
    }

    private void fireTrigger(TriggerType type, Map<String, String> context) {
        TriggerSink sink = scriptSink;
        if (sink != null) {
            sink.fire(type, instance.instanceId(), instance.dungeonId(), context);
        }
    }

    private Map<String, String> playerCtx(UUID playerId, String key, String value) {
        Map<String, String> context = new LinkedHashMap<>();
        if (playerId != null) {
            context.put("player", playerId.toString());
        }
        if (key != null && value != null) {
            context.put(key, value);
        }
        return context;
    }

    public FlowDefinition definition() {
        return definition;
    }

    public Adjudicator adjudicator() {
        return adjudicator;
    }

    public DungeonInstance instance() {
        return instance;
    }

    public void setFact(String key, Object value) {
        if (value == null) {
            facts.remove(key);
        } else {
            facts.put(key, value);
        }
    }

    public Object fact(String key) {
        return facts.get(key);
    }

    public Map<String, Object> factsView() {
        return Map.copyOf(facts);
    }

    public int factInt(String key, int fallback) {
        Object value = facts.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? fallback : Integer.parseInt(String.valueOf(value));
    }

    public boolean factFlag(String key) {
        return Boolean.TRUE.equals(facts.get(key));
    }

    public boolean isGateOpened(String gateId) {
        return openedGates.contains(gateId);
    }

    public boolean hasTriggeredSpawn(String spawnId) {
        return triggeredSpawns.contains(spawnId);
    }

    public void markSpawnTriggered(String spawnId) {
        triggeredSpawns.add(spawnId);
    }

    public int currentRoomOrder() {
        return currentRoomOrder;
    }

    public String checkpointId() {
        return checkpointId;
    }

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

    private void onChestOpen(IngressEvent event) {
        FlowModules.RewardDef reward = definition.rewards().get(event.sourceId());
        if (reward == null || !reward.head().enabled()) {
            return;
        }
        UUID playerId = EventIngressKeys.player(event.payload());
        EvalContext context = new EvalContext(playerId, event.payload());
        if (!adjudicator.adjudicate(reward.head(), reward.conditions(), context, playerId).allowed()) {
            return;
        }
        fireTrigger(TriggerType.onChestOpen,
                playerCtx(playerId, "chestId", event.sourceId()));
        if (controllers != null) {
            controllers.handleRewardOpen(this, event.sourceId(), playerId);
        }
    }

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
    }

    private void handleRoomZone(String roomId, UUID playerId, Map<String, Object> payload) {
        if (scriptTakeover) {
            return;
        }
        for (FlowModules.RoomDef room : definition.rooms()) {
            if (!room.id().equals(roomId)) {
                return;
            }
            boolean entered = Boolean.TRUE.equals(payload.get(Facts.REGION_ENTERED));
            if (!entered) {
                return;
            }
            if (currentRoomOrder == Integer.MIN_VALUE) {
                currentRoomOrder = room.order();
                notifyRoomEntered(room.id(), playerId);
                return;
            }
            if (room.order() == currentRoomOrder + 1) {
                String previousRoomId = currentRoomId();
                currentRoomOrder = room.order();
                notifyRoomEntered(room.id(), playerId);
                syncStragglers(previousRoomId, playerId);
            }
            return;
        }
    }

    private String currentRoomId() {
        for (FlowModules.RoomDef room : definition.rooms()) {
            if (room.order() == currentRoomOrder) {
                return room.id();
            }
        }
        return "";
    }

    private void notifyRoomEntered(String roomId, UUID playerId) {
        if (controllers != null) {
            controllers.handleRoomEntered(this, roomId, playerId);
        }
        fireTrigger(TriggerType.onRoomEnter, playerCtx(playerId, "roomId", roomId));
    }

    public void scriptEnterRoom(String roomId) {
        FlowModules.RoomDef target = null;
        for (FlowModules.RoomDef room : definition.rooms()) {
            if (room.id().equals(roomId)) {
                target = room;
                break;
            }
        }
        if (target == null || target.region() == null) {
            return;
        }
        String previousRoomId = currentRoomId();
        currentRoomOrder = target.order();
        notifyRoomEntered(target.id(), null);
        if (previousRoomId.isEmpty()) {
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
        World world = Bukkit.getWorld(instance.instanceId());
        if (world == null) {
            return;
        }
        UUID anchor = null;
        for (Player member : world.getPlayers()) {
            Location location = member.getLocation();
            if (target.region().contains(location.getX(), location.getY(), location.getZ())) {
                anchor = member.getUniqueId();
                break;
            }
        }
        if (anchor != null) {
            syncStragglers(previousRoomId, anchor);
        }
    }

    public void scriptHint(String text, String channel) {
        submitHint(null, text, channel == null || channel.isBlank() ? "chat" : channel);
    }

    private void syncStragglers(String previousRoomId, UUID arrivingPlayer) {
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

    private void onRange(IngressEvent event) {
        String spawnId = event.sourceId();
        FlowModules.SpawnPointModuleDef spawnPoint = definition.spawnPoints().get(spawnId);
        if (spawnPoint == null || !spawnPoint.head().enabled()) {
            return;
        }
        if (spawnPoint.once() && hasTriggeredSpawn(spawnId)) {
            return;
        }
        int playersInRange = event.payload().get(Facts.RANGE_PLAYERS) instanceof Number number
                ? number.intValue() : 0;
        if (playersInRange < spawnPoint.minPlayers()) {
            return;
        }
        UUID playerId = EventIngressKeys.player(event.payload());
        EvalContext context = new EvalContext(playerId, event.payload());
        if (!adjudicator.adjudicate(spawnPoint.head(), List.of(), context, playerId).allowed()) {
            return;
        }
        markSpawnTriggered(spawnId);
        if (controllers != null && !spawnPoint.waves().isEmpty()) {
            controllers.spawnWave(this, spawnId, 0, playerId);
        }
    }

    private void onBlockClick(IngressEvent event) {
        FlowModules.BlockInteractionDef interaction = definition.blockInteractions().get(event.sourceId());
        if (interaction == null || !interaction.head().enabled()) {
            return;
        }
        UUID playerId = EventIngressKeys.player(event.payload());
        EvalContext context = new EvalContext(playerId, event.payload());
        if (!adjudicator.adjudicate(interaction.head(), interaction.conditions(), context, playerId).allowed()) {
            return;
        }
        fireTrigger(TriggerType.onBlockInteract,
                playerCtx(playerId, "blockId", event.sourceId()));
        executeActions(interaction.actions(), context, playerId);
    }

    private void onItemInteract(IngressEvent event) {
        FlowModules.ItemInteractionDef interaction = definition.itemInteractions().get(event.sourceId());
        if (interaction == null || !interaction.head().enabled()) {
            return;
        }
        UUID playerId = EventIngressKeys.player(event.payload());
        EvalContext context = new EvalContext(playerId, event.payload());
        if (!adjudicator.adjudicate(interaction.head(), interaction.conditions(), context, playerId).allowed()) {
            return;
        }
        executeActions(interaction.actions(), context, playerId);
    }

    private void onEntityDeath(IngressEvent event) {
        String mobId = String.valueOf(event.payload().getOrDefault("mob", ""));
        if (!mobId.isEmpty()) {
            String key = Facts.killCount(mobId);
            setFact(key, factInt(key, 0) + 1);
        }
        if (controllers != null) {
            controllers.handleMobDeath(this, EventIngressKeys.entity(event.payload()));
        }
        evaluateKillTargets(mobId, EventIngressKeys.player(event.payload()));
        evaluateGates(EventIngressKeys.player(event.payload()));
    }

    private void evaluateKillTargets(String mobId, UUID playerId) {
        if (mobId.isEmpty()) {
            return;
        }
        EvalContext context = new EvalContext(playerId, facts);
        for (Map.Entry<String, FlowModules.KillTargetDef> entry : definition.killTargets().entrySet()) {
            FlowModules.KillTargetDef killTarget = entry.getValue();
            if (!killTarget.head().enabled() || !mobId.equals(killTarget.target())) {
                continue;
            }
            String countKey = Facts.killCount(killTarget.target());
            if (factInt(countKey, 0) < killTarget.count()) {
                continue;
            }
            if (!triggeredSpawns.add("kill_target:" + entry.getKey())) {
                continue;
            }
            if (adjudicator.adjudicate(killTarget.head(), List.of(), context, playerId).allowed()) {
                fireTrigger(TriggerType.onKillCount, playerCtx(playerId, "count",
                        String.valueOf(factInt(countKey, 0))));
                executeActions(killTarget.actions(), context, playerId);
            }
        }
    }

    private void onActivate(IngressEvent event) {
        FlowModules.MechanismDef mechanism = definition.mechanisms().get(event.sourceId());
        if (mechanism == null || !mechanism.head().enabled()) {
            return;
        }
        UUID playerId = EventIngressKeys.player(event.payload());
        EvalContext context = new EvalContext(playerId, event.payload());
        if (!adjudicator.adjudicate(mechanism.head(), mechanism.conditions(), context, playerId).allowed()) {
            return;
        }
        fireTrigger(TriggerType.onBlockActivate,
                playerCtx(playerId, "mechanismId", event.sourceId()));
        for (FlowModules.BlockStateTarget target : mechanism.targets()) {
            submitSetBlock(playerId, target);
        }
    }

    private void onRedstone(IngressEvent event) {
        FlowModules.MechanismDef mechanism = definition.mechanisms().get(event.sourceId());
        if (mechanism == null || !mechanism.head().enabled()) {
            return;
        }
        UUID playerId = EventIngressKeys.player(event.payload());
        EvalContext context = new EvalContext(playerId, event.payload());
        if (!adjudicator.adjudicate(mechanism.head(), mechanism.conditions(), context, playerId).allowed()) {
            return;
        }
        fireTrigger(TriggerType.onRedstoneSignal,
                playerCtx(playerId, "mechanismId", event.sourceId()));
        for (FlowModules.BlockStateTarget target : mechanism.targets()) {
            submitSetBlock(playerId, target);
        }
    }

    public void evaluateGates(UUID playerId) {
        EvalContext context = new EvalContext(playerId, facts);
        for (FlowModules.GateDef gate : definition.gates().values()) {
            if (!gate.head().enabled() || isGateOpened(gate.gateId())) {
                continue;
            }
            if (!adjudicator.evaluator().evaluateAll(gate.conditions(), gate.logic(), context)) {
                continue;
            }
            if (adjudicator.counter().check(gate.head(), playerId) != TriggerCounter.ExhaustedTier.NONE) {
                continue;
            }
            openedGates.add(gate.gateId());
            if (controllers != null) {
                controllers.openGate(this, gate.gateId(), playerId);
            }
        }
        if (controllers != null) {
            controllers.syncBossPoints(this);
        }
    }

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

    public void executeActions(List<FlowActionDef> actions, EvalContext context, UUID playerId) {
        for (FlowActionDef action : actions) {
            executeAction(action, context, playerId);
        }
    }

    private void executeAction(FlowActionDef action, EvalContext context, UUID playerId) {
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
                if (controllers != null) {
                    controllers.triggerFail(this, action.param("reason", "scripted"));
                }
            }
            case "complete" -> {
                anchor("complete action | instance=" + instance.instanceId()
                        + " | cause=" + action.param("cause", "flow")
                        + " | controllers=" + (controllers != null));
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

    private void applyEffectSpecs(Player player, List<String> effects) {
        for (String raw : effects) {
            String[] parts = raw.split(":");
            if (parts.length == 0 || parts[0].isBlank()) {
                continue;
            }
            try {
                PotionEffectType type =
                        PotionEffectType.getByName(parts[0].toUpperCase(Locale.ROOT));
                if (type == null) {
                    continue;
                }
                int amplifier = parts.length > 1 ? parseSafe(parts[1], 0) : 0;
                int duration = parts.length > 2 ? parseSafe(parts[2], 100) : 100;
                boolean clear = "clear".equalsIgnoreCase(parts[0]);
                if (clear) {
                    player.removePotionEffect(type);
                } else {
                    player.addPotionEffect(new PotionEffect(
                            type, duration, amplifier, true, true));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static int parseSafe(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void submitHint(UUID playerId, String text, String channel) {
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

    private void showHint(Player player, Component component, String channel) {
        if ("title".equals(channel)) {
            player.showTitle(Title.title(
                    component, Component.empty(),
                    Title.Times.times(
                    Duration.ofMillis(100),
                    Duration.ofMillis(600),
                    Duration.ofMillis(100))));
        } else if ("actionbar".equals(channel)) {
            player.sendActionBar(component);
        } else {
            player.sendMessage(component);
        }
    }

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

    private List<UUID> audienceOf(UUID playerId) {
        if (playerId != null) {
            return List.of(playerId);
        }
        World world = Bukkit.getWorld(instance.instanceId());
        if (world == null) {
            return List.of();
        }
        List<UUID> audience = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            audience.add(player.getUniqueId());
        }
        return audience;
    }

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
                    try {
                        world.playSound(world.getSpawnLocation(),
                                Sound.valueOf(sound.toUpperCase(Locale.ROOT)),
                                1.0F, 1.0F);
                    } catch (IllegalArgumentException ignored) {
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
                    }
                }
            }
        });
    }

    private void submitSetBlock(UUID playerId, FlowModules.BlockStateTarget target) {
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

    private String replacePlayer(String commandLine, UUID playerId) {
        if (playerId == null) {
            return commandLine;
        }
        Player player = Bukkit.getPlayer(playerId);
        String name = player == null ? "" : player.getName();
        return commandLine.replace("%player%", name);
    }
}
