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

final class FlowRuntimeDelegate {

    private final FlowRuntime ownerRef;

    FlowRuntimeDelegate(FlowRuntime ownerRef) {
        this.ownerRef = ownerRef;
    }

    void bindControllers(FlowControllers controllers) {
        ownerRef.controllers = controllers;
    }

    void bindStateHook(PlayerStateHook stateHook) {
        ownerRef.stateHook = stateHook;
    }

    void bindScript(TriggerSink scriptSink) {
        ownerRef.scriptSink = scriptSink;
    }

    void setScriptTakeover(boolean scriptTakeover) {
        ownerRef.scriptTakeover = scriptTakeover;
    }

    void bindDebug(DebugLogger debug) {
        ownerRef.debug = debug;
    }

    void anchor(String message) {
        DebugLogger logger = ownerRef.debug;
        if (logger != null) {
            logger.logRaw("dungeon", (UUID) null, message);
        }
    }

    long instanceGeneration() {
        return ownerRef.generation.get();
    }

    boolean isCurrentGeneration(long expected) {
        return ownerRef.running && ownerRef.generation.get() == expected;
    }

    void shutdown() {
        ownerRef.running = false;
        ownerRef.generation.incrementAndGet();
        for (TaskToken token : ownerRef.scheduledTasks) {
            if (!token.cancelled()) {
                token.cancel();
            }
        }
        ownerRef.scheduledTasks.clear();
    }

    boolean schedule(Supplier<TaskToken> action, String description) {
        if (!ownerRef.running) {
            return false;
        }
        TaskToken token;
        try {
            token = action.get();
        } catch (RuntimeException exception) {
            DebugLogger logger = ownerRef.debug;
            if (logger != null) {
                logger.logRaw("dungeon", (UUID) null, "调度失败 | instance=" + ownerRef.instance.instanceId()
                        + " | task=" + description + " | error=" + exception.getMessage());
            }
            return false;
        }
        if (token == null || token.cancelled()) {
            return false;
        }
        ownerRef.scheduledTasks.add(token);
        return true;
    }

    boolean scheduleGlobal(long delayTicks, String description, Runnable task) {
        long expected = ownerRef.generation.get();
        return ownerRef.schedule(() -> ownerRef.dispatcher.runGlobalLater(ownerRef.plugin, () -> {
            if (!ownerRef.isCurrentGeneration(expected)) {
                ownerRef.anchor("scheduled task discarded | instance=" + ownerRef.instance.instanceId()
                        + " | task=" + description + " | reason=实例代际已失效");
                return;
            }
            task.run();
        }, delayTicks), description);
    }

    boolean isScriptTakeover() {
        return ownerRef.scriptTakeover;
    }

    void fireTrigger(TriggerType type, Map<String, String> context) {
        TriggerSink sink = ownerRef.scriptSink;
        if (sink != null) {
            sink.fire(type, ownerRef.instance.instanceId(), ownerRef.instance.dungeonId(), context);
        }
    }

    Map<String, String> playerCtx(UUID playerId, String key, String value) {
        Map<String, String> context = new LinkedHashMap<>();
        if (playerId != null) {
            context.put("player", playerId.toString());
        }
        if (key != null && value != null) {
            context.put(key, value);
        }
        return context;
    }

    FlowDefinition definition() {
        return ownerRef.definition;
    }

    Adjudicator adjudicator() {
        return ownerRef.adjudicator;
    }

    DungeonInstance instance() {
        return ownerRef.instance;
    }

    void setFact(String key, Object value) {
        if (value == null) {
            ownerRef.facts.remove(key);
        } else {
            ownerRef.facts.put(key, value);
        }
    }

    Object fact(String key) {
        return ownerRef.facts.get(key);
    }

    Map<String, Object> factsView() {
        return Map.copyOf(ownerRef.facts);
    }

    int openedGateCount() {
        return ownerRef.openedGates.size();
    }

    int triggeredSpawnCount() {
        return ownerRef.triggeredSpawns.size();
    }

    int factInt(String key, int fallback) {
        Object value = ownerRef.facts.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? fallback : Integer.parseInt(String.valueOf(value));
    }

    boolean factFlag(String key) {
        return Boolean.TRUE.equals(ownerRef.facts.get(key));
    }

    boolean isGateOpened(String gateId) {
        return ownerRef.openedGates.contains(gateId);
    }

    boolean hasTriggeredSpawn(String spawnId) {
        return ownerRef.triggeredSpawns.contains(spawnId);
    }

    void markSpawnTriggered(String spawnId) {
        ownerRef.triggeredSpawns.add(spawnId);
    }

    int currentRoomOrder() {
        return ownerRef.currentRoomOrder;
    }

    String checkpointId() {
        return ownerRef.checkpointId;
    }

    void onChestOpen(IngressEvent event) {
        FlowModules.RewardDef reward = ownerRef.definition.rewards().get(event.sourceId());
        if (reward == null || !reward.head().enabled()) {
            return;
        }
        UUID playerId = EventIngressKeys.player(event.payload());
        EvalContext context = new EvalContext(playerId, event.payload());
        if (!ownerRef.adjudicator.adjudicate(reward.head(), reward.conditions(), context, playerId).allowed()) {
            return;
        }
        ownerRef.fireTrigger(TriggerType.onChestOpen,
                playerCtx(playerId, "chestId", event.sourceId()));
        if (ownerRef.controllers != null) {
            ownerRef.controllers.handleRewardOpen(ownerRef, event.sourceId(), playerId);
        }
    }

    void handleRoomZone(String roomId, UUID playerId, Map<String, Object> payload) {
        if (ownerRef.scriptTakeover) {
            return;
        }
        for (FlowModules.RoomDef room : ownerRef.definition.rooms()) {
            if (!room.id().equals(roomId)) {
                return;
            }
            boolean entered = Boolean.TRUE.equals(payload.get(Facts.REGION_ENTERED));
            if (!entered) {
                return;
            }
            if (ownerRef.currentRoomOrder == Integer.MIN_VALUE) {
                ownerRef.currentRoomOrder = room.order();
                ownerRef.notifyRoomEntered(room.id(), playerId);
                return;
            }
            if (room.order() == ownerRef.currentRoomOrder + 1) {
                String previousRoomId = ownerRef.currentRoomId();
                ownerRef.currentRoomOrder = room.order();
                ownerRef.notifyRoomEntered(room.id(), playerId);
                ownerRef.syncStragglers(previousRoomId, playerId);
            }
            return;
        }
    }

    String currentRoomId() {
        for (FlowModules.RoomDef room : ownerRef.definition.rooms()) {
            if (room.order() == ownerRef.currentRoomOrder) {
                return room.id();
            }
        }
        return "";
    }

    void notifyRoomEntered(String roomId, UUID playerId) {
        ownerRef.recordFlowEvent("room_enter", "room=" + roomId);
        if (ownerRef.controllers != null) {
            ownerRef.controllers.handleRoomEntered(ownerRef, roomId, playerId);
        }
        ownerRef.fireTrigger(TriggerType.onRoomEnter, playerCtx(playerId, "roomId", roomId));
    }

    void scriptEnterRoom(String roomId) {
        FlowModules.RoomDef target = null;
        for (FlowModules.RoomDef room : ownerRef.definition.rooms()) {
            if (room.id().equals(roomId)) {
                target = room;
                break;
            }
        }
        if (target == null || target.region() == null) {
            return;
        }
        String previousRoomId = ownerRef.currentRoomId();
        ownerRef.currentRoomOrder = target.order();
        ownerRef.notifyRoomEntered(target.id(), null);
        if (previousRoomId.isEmpty()) {
            return;
        }
        FlowModules.RoomDef previousRoom = null;
        for (FlowModules.RoomDef room : ownerRef.definition.rooms()) {
            if (room.id().equals(previousRoomId)) {
                previousRoom = room;
                break;
            }
        }
        if (previousRoom == null || previousRoom.region() == null) {
            return;
        }
        World world = Bukkit.getWorld(ownerRef.instance.instanceId());
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
            ownerRef.syncStragglers(previousRoomId, anchor);
        }
    }

    void scriptHint(String text, String channel) {
        ownerRef.submitHint(null, text, channel == null || channel.isBlank() ? "chat" : channel);
    }

    void onRange(IngressEvent event) {
        String spawnId = event.sourceId();
        FlowModules.SpawnPointModuleDef spawnPoint = ownerRef.definition.spawnPoints().get(spawnId);
        if (spawnPoint == null || !spawnPoint.head().enabled()) {
            return;
        }
        if (spawnPoint.once() && ownerRef.hasTriggeredSpawn(spawnId)) {
            return;
        }
        int playersInRange = event.payload().get(Facts.RANGE_PLAYERS) instanceof Number number
                ? number.intValue() : 0;
        if (playersInRange < spawnPoint.minPlayers()) {
            return;
        }
        UUID playerId = EventIngressKeys.player(event.payload());
        EvalContext context = new EvalContext(playerId, event.payload());
        if (!ownerRef.adjudicator.adjudicate(spawnPoint.head(), List.of(), context, playerId).allowed()) {
            return;
        }
        ownerRef.markSpawnTriggered(spawnId);
        ownerRef.recordFlowEvent("spawn_wave", "spawn=" + spawnId);
        if (ownerRef.controllers != null && !spawnPoint.waves().isEmpty()) {
            ownerRef.controllers.spawnWave(ownerRef, spawnId, 0, playerId);
        }
    }

    void onBlockClick(IngressEvent event) {
        FlowModules.BlockInteractionDef interaction = ownerRef.definition.blockInteractions().get(event.sourceId());
        if (interaction == null || !interaction.head().enabled()) {
            return;
        }
        UUID playerId = EventIngressKeys.player(event.payload());
        EvalContext context = new EvalContext(playerId, event.payload());
        if (!ownerRef.adjudicator.adjudicate(interaction.head(), interaction.conditions(), context, playerId).allowed()) {
            return;
        }
        ownerRef.fireTrigger(TriggerType.onBlockInteract,
                playerCtx(playerId, "blockId", event.sourceId()));
        ownerRef.executeActions(interaction.actions(), context, playerId);
    }

    void onItemInteract(IngressEvent event) {
        FlowModules.ItemInteractionDef interaction = ownerRef.definition.itemInteractions().get(event.sourceId());
        if (interaction == null || !interaction.head().enabled()) {
            return;
        }
        UUID playerId = EventIngressKeys.player(event.payload());
        EvalContext context = new EvalContext(playerId, event.payload());
        if (!ownerRef.adjudicator.adjudicate(interaction.head(), interaction.conditions(), context, playerId).allowed()) {
            return;
        }
        ownerRef.executeActions(interaction.actions(), context, playerId);
    }

    void onEntityDeath(IngressEvent event) {
        String mobId = String.valueOf(event.payload().getOrDefault("mob", ""));
        if (!mobId.isEmpty()) {
            String key = Facts.killCount(mobId);
            ownerRef.setFact(key, factInt(key, 0) + 1);
        }
        if (ownerRef.controllers != null) {
            ownerRef.controllers.handleMobDeath(ownerRef, EventIngressKeys.entity(event.payload()));
        }
        ownerRef.evaluateKillTargets(mobId, EventIngressKeys.player(event.payload()));
        ownerRef.evaluateGates(EventIngressKeys.player(event.payload()));
    }

    void evaluateKillTargets(String mobId, UUID playerId) {
        if (mobId.isEmpty()) {
            return;
        }
        EvalContext context = new EvalContext(playerId, ownerRef.facts);
        for (Map.Entry<String, FlowModules.KillTargetDef> entry : ownerRef.definition.killTargets().entrySet()) {
            FlowModules.KillTargetDef killTarget = entry.getValue();
            if (!killTarget.head().enabled() || !mobId.equals(killTarget.target())) {
                continue;
            }
            String countKey = Facts.killCount(killTarget.target());
            if (ownerRef.factInt(countKey, 0) < killTarget.count()) {
                continue;
            }
            if (!ownerRef.triggeredSpawns.add("kill_target:" + entry.getKey())) {
                continue;
            }
            if (ownerRef.adjudicator.adjudicate(killTarget.head(), List.of(), context, playerId).allowed()) {
                ownerRef.fireTrigger(TriggerType.onKillCount, playerCtx(playerId, "count",
                        String.valueOf(ownerRef.factInt(countKey, 0))));
                ownerRef.executeActions(killTarget.actions(), context, playerId);
            }
        }
    }

    void onActivate(IngressEvent event) {
        FlowModules.MechanismDef mechanism = ownerRef.definition.mechanisms().get(event.sourceId());
        if (mechanism == null || !mechanism.head().enabled()) {
            return;
        }
        UUID playerId = EventIngressKeys.player(event.payload());
        EvalContext context = new EvalContext(playerId, event.payload());
        if (!ownerRef.adjudicator.adjudicate(mechanism.head(), mechanism.conditions(), context, playerId).allowed()) {
            return;
        }
        ownerRef.fireTrigger(TriggerType.onBlockActivate,
                playerCtx(playerId, "mechanismId", event.sourceId()));
        for (FlowModules.BlockStateTarget target : mechanism.targets()) {
            ownerRef.submitSetBlock(playerId, target);
        }
    }

    void onRedstone(IngressEvent event) {
        FlowModules.MechanismDef mechanism = ownerRef.definition.mechanisms().get(event.sourceId());
        if (mechanism == null || !mechanism.head().enabled()) {
            return;
        }
        UUID playerId = EventIngressKeys.player(event.payload());
        EvalContext context = new EvalContext(playerId, event.payload());
        if (!ownerRef.adjudicator.adjudicate(mechanism.head(), mechanism.conditions(), context, playerId).allowed()) {
            return;
        }
        ownerRef.fireTrigger(TriggerType.onRedstoneSignal,
                playerCtx(playerId, "mechanismId", event.sourceId()));
        for (FlowModules.BlockStateTarget target : mechanism.targets()) {
            ownerRef.submitSetBlock(playerId, target);
        }
    }

    void evaluateGates(UUID playerId) {
        EvalContext context = new EvalContext(playerId, ownerRef.facts);
        for (FlowModules.GateDef gate : ownerRef.definition.gates().values()) {
            if (!gate.head().enabled() || ownerRef.isGateOpened(gate.gateId())) {
                continue;
            }
            if (!ownerRef.adjudicator.evaluator().evaluateAll(gate.conditions(), gate.logic(), context)) {
                continue;
            }
            if (ownerRef.adjudicator.counter().check(gate.head(), playerId) != TriggerCounter.ExhaustedTier.NONE) {
                continue;
            }
            ownerRef.openedGates.add(gate.gateId());
            ownerRef.recordFlowEvent("gate_open", "gate=" + gate.gateId());
            if (ownerRef.controllers != null) {
                ownerRef.controllers.openGate(ownerRef, gate.gateId(), playerId);
            }
        }
        if (ownerRef.controllers != null) {
            ownerRef.controllers.syncBossPoints(ownerRef);
        }
    }

    void executeActions(List<FlowActionDef> actions, EvalContext context, UUID playerId) {
        for (FlowActionDef action : actions) {
            ownerRef.executeAction(action, context, playerId);
        }
    }

    void applyEffectSpecs(Player player, List<String> effects) {
        for (String raw : effects) {
            String[] parts = raw.split(":");
            if (parts.length == 0 || parts[0].isBlank()) {
                continue;
            }
            try {
                PotionEffectType type = Registry.MOB_EFFECT.get(
                        NamespacedKey.fromString(parts[0].toLowerCase(Locale.ROOT)));
                if (type == null) {
                    continue;
                }
                int amplifier = parts.length > 1 ? FlowRuntimeSupport.parseSafe(parts[1], 0) : 0;
                int duration = parts.length > 2 ? FlowRuntimeSupport.parseSafe(parts[2], 100) : 100;
                boolean clear = "clear".equalsIgnoreCase(parts[0]);
                if (clear) {
                    player.removePotionEffect(type);
                } else {
                    player.addPotionEffect(new PotionEffect(
                            type, duration, amplifier, true, true));
                }
            } catch (IllegalArgumentException ignored) {
                FailureRecorder.recordIgnoredFailure("EmakiDungeon", "FlowRuntime.java#1", ignored);
            }
        }
    }

    void showHint(Player player, Component component, String channel) {
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

    List<UUID> audienceOf(UUID playerId) {
        if (playerId != null) {
            return List.of(playerId);
        }
        World world = Bukkit.getWorld(ownerRef.instance.instanceId());
        if (world == null) {
            return List.of();
        }
        List<UUID> audience = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            audience.add(player.getUniqueId());
        }
        return audience;
    }

    String replacePlayer(String commandLine, UUID playerId) {
        if (playerId == null) {
            return commandLine;
        }
        Player player = Bukkit.getPlayer(playerId);
        String name = player == null ? "" : player.getName();
        return commandLine.replace("%player%", name);
    }

}
