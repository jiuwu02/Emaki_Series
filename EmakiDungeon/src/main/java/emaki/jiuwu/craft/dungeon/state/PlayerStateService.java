package emaki.jiuwu.craft.dungeon.state;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.flow.FlowModules;
import emaki.jiuwu.craft.dungeon.flow.FlowPoint;
import emaki.jiuwu.craft.dungeon.flow.FlowRuntime;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.WorldCommand;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceCoordinator;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.party.Party;
import emaki.jiuwu.craft.dungeon.party.PartyService;
import emaki.jiuwu.craft.dungeon.recovery.RecoveryService;

public final class PlayerStateService {

    private static final class LifeState {

        private final Map<UUID, Integer> lives = new ConcurrentHashMap<>();
        private volatile long deadline;
        private volatile boolean failed;
    }

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final InstanceCoordinator coordinator;
    private final InstanceRegistry registry;
    private final PartyService parties;
    private final RecoveryService recovery;
    private final MessageService messages;
    private final DebugLogger debug;
    private final Function<String, DungeonConfig> configOf;
    private final emaki.jiuwu.craft.corelib.loader.LanguageLoader languageLoader;
    private volatile emaki.jiuwu.craft.dungeon.action.ActionBridge actionBridge;

    private final Map<String, LifeState> states = new ConcurrentHashMap<>();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<Integer>> warnedMarks = new ConcurrentHashMap<>();

    private TaskToken tickTask = TaskToken.UNAVAILABLE;

    public PlayerStateService(Plugin plugin,
            ExecutionDispatcher dispatcher,
            InstanceCoordinator coordinator,
            InstanceRegistry registry,
            PartyService parties,
            RecoveryService recovery,
            MessageService messages,
            DebugLogger debug,
            Function<String, DungeonConfig> configOf,
            emaki.jiuwu.craft.corelib.loader.LanguageLoader languageLoader) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.coordinator = coordinator;
        this.registry = registry;
        this.parties = parties;
        this.recovery = recovery;
        this.messages = messages;
        this.debug = debug;
        this.configOf = configOf;
        this.languageLoader = languageLoader;
    }

    public void start() {
        tickTask = dispatcher.runGlobalTimer(plugin, this::tick, 20L, 20L);
    }

    public void bindActionBridge(emaki.jiuwu.craft.dungeon.action.ActionBridge actionBridge) {
        this.actionBridge = actionBridge;
    }

    public void shutdown() {
        tickTask.cancel();
        states.clear();
        spectators.clear();
    }

    public void register(DungeonInstance instance) {
        DungeonConfig config = configOf.apply(instance.dungeonId());
        LifeState state = new LifeState();
        int respawnCount = config == null
                ? 3
                : config.respawnCounts().getOrDefault(instance.difficulty(), 3);
        long timeoutSeconds = config == null ? 1800L : config.timeoutSeconds();
        state.deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        Party party = instance.partyId() == null ? null : parties.partyById(instance.partyId());
        if (party != null) {
            for (UUID memberId : party.members()) {
                state.lives.put(memberId, respawnCount);
            }
        }
        states.put(instance.instanceId(), state);
    }

    public void onPlayerDeath(DungeonInstance instance, UUID playerId) {
        LifeState state = states.get(instance.instanceId());
        if (state == null || state.failed) {
            return;
        }
        int remaining = state.lives.merge(playerId, -1, Integer::sum);
        if (remaining < 0) {
            state.lives.put(playerId, 0);
            remaining = 0;
        }
        FlowRuntime runtime = instance.flowRuntime();
        if (runtime != null) {
            runtime.setFact(emaki.jiuwu.craft.dungeon.flow.Facts.PLAYER_STATE, "dead");
            runtime.setFact(emaki.jiuwu.craft.dungeon.flow.Facts.REMAINING_LIVES, remaining);
        }
        debug.log("dungeon", (UUID) null, "player_died", Map.of(
                "player", String.valueOf(playerId),
                "instance", instance.instanceId(),
                "remaining", String.valueOf(remaining)
        ));
        messages.warning("player.died", Map.of(
                "player", String.valueOf(playerId),
                "remaining", String.valueOf(remaining)
        ));
        if (remaining <= 0) {
            toSpectator(instance, playerId);
        }
        maybeFail(instance, "all_dead");
    }

    public Location resolveRespawn(DungeonInstance instance, Player player) {
        World world = Bukkit.getWorld(instance.instanceId());
        if (world == null) {
            return null;
        }
        FlowRuntime runtime = instance.flowRuntime();
        if (runtime == null) {
            return world.getSpawnLocation();
        }
        String checkpoint = runtime.checkpointId();
        if (!checkpoint.isEmpty()) {
            for (FlowModules.CheckpointDef checkpointDef : runtime.definition().checkpoints().values()) {
                if (checkpointDef.recordId().equals(checkpoint) && checkpointDef.point() != null) {
                    return toLocation(world, checkpointDef.point());
                }
            }
        }
        FlowModules.RespawnPointDef respawnPoint = findRespawn(runtime, checkpoint);
        if (respawnPoint != null && respawnPoint.point() != null) {
            return toLocation(world, respawnPoint.point());
        }
        if (runtime.definition().spawnPoint() != null
                && runtime.definition().spawnPoint().point() != null) {
            return toLocation(world, runtime.definition().spawnPoint().point());
        }
        return world.getSpawnLocation();
    }

    private FlowModules.RespawnPointDef findRespawn(FlowRuntime runtime, String checkpointId) {
        FlowModules.RespawnPointDef linked = null;
        for (FlowModules.RespawnPointDef respawnPoint : runtime.definition().respawnPoints().values()) {
            if (!checkpointId.isEmpty() && checkpointId.equals(respawnPoint.checkpointId())) {
                return respawnPoint;
            }
            if (linked == null) {
                linked = respawnPoint;
            }
        }
        return linked;
    }

    private static Location toLocation(World world, FlowPoint point) {
        return new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
    }

    private void toSpectator(DungeonInstance instance, UUID playerId) {
        if (!spectators.add(playerId)) {
            return;
        }
        instance.commands().submit(new WorldCommand() {
            @Override
            public int blockCost() {
                return 0;
            }

            @Override
            public void execute() {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            }
        });
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            messages.send(player, "flow.spectator.entered", Map.of());
        }
    }

    public boolean isSpectator(UUID playerId) {
        return playerId != null && spectators.contains(playerId);
    }

    public void maybeFail(DungeonInstance instance, String reason) {
        LifeState state = states.get(instance.instanceId());
        if (state == null || state.failed) {
            return;
        }
        Party party = instance.partyId() == null ? null : parties.partyById(instance.partyId());
        if (party == null) {
            return;
        }
        boolean anyMemberInside = false;
        for (UUID memberId : party.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                continue;
            }
            String pointer = recovery.pointerOf(memberId);
            if (instance.instanceId().equals(pointer)) {
                anyMemberInside = true;
                if (!spectators.contains(memberId)) {
                    return;
                }
            }
        }
        if (anyMemberInside) {
            fail(instance, reason);
        }
    }

    public void fail(DungeonInstance instance, String reason) {
        LifeState state = states.get(instance.instanceId());
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (state.failed) {
                return;
            }
            state.failed = true;
        }
        debug.logRaw("dungeon", (UUID) null,
                "instance failed | instance=" + instance.instanceId() + " | reason=" + reason);
        if (actionBridge != null) {
            actionBridge.dispatch("dungeon_fail", null, Map.of(
                    "instanceId", instance.instanceId(),
                    "reason", reason
            ));
        }
        dispatcher.runGlobal(plugin, () -> {
            Party party = instance.partyId() == null ? null : parties.partyById(instance.partyId());
            if (party != null) {
                for (UUID memberId : party.members()) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member == null) {
                        continue;
                    }
                    if (instance.instanceId().equals(recovery.pointerOf(memberId))) {
                        messages.send(member, "flow.fail.returned", Map.of("reason", reason));
                        recovery.onExit(member, "fail:" + reason);
                    }
                }
            }
            coordinator.dispose(instance.instanceId(), "fail:" + reason);
        });
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (DungeonInstance instance : registry.all()) {
            LifeState state = states.get(instance.instanceId());
            if (state == null || state.failed) {
                continue;
            }
            if (state.deadline > 0 && now > state.deadline) {
                fail(instance, "timeout");
                continue;
            }
            warnTimerMarks(instance, state, now);
            followSpectators(instance);
            maybeFail(instance, "all_dead");
        }
    }

    private void warnTimerMarks(DungeonInstance instance, LifeState state, long now) {
        if (state.deadline <= 0 || actionBridge == null) {
            return;
        }
        DungeonConfig config = configOf.apply(instance.dungeonId());
        if (config == null) {
            return;
        }
        long remainingSeconds = Math.max(0, (state.deadline - now) / 1000L);
        Set<Integer> warned = warnedMarks.computeIfAbsent(instance.instanceId(), key -> ConcurrentHashMap.newKeySet());
        for (int mark : config.timerMessages()) {
            if (remainingSeconds > mark || warned.contains(mark)) {
                continue;
            }
            warned.add(mark);
            String template = languageLoader.getMessage("flow.timer.warning");
            String text = emaki.jiuwu.craft.corelib.api.text.Texts.formatTemplate(
                    template == null ? "剩余 %seconds% 秒" : template,
                    Map.of("seconds", String.valueOf(mark)));
            actionBridge.sendToWorld(instance.instanceId(), text);
            debug.logRaw("dungeon", (UUID) null,
                    "timer mark | instance=" + instance.instanceId() + " | seconds=" + mark);
        }
    }

    private void followSpectators(DungeonInstance instance) {
        World world = Bukkit.getWorld(instance.instanceId());
        if (world == null) {
            return;
        }
        Player target = null;
        for (Player member : world.getPlayers()) {
            if (!isSpectator(member.getUniqueId())) {
                target = member;
                break;
            }
        }
        if (target == null) {
            return;
        }
        for (Player member : world.getPlayers()) {
            if (!isSpectator(member.getUniqueId())) {
                continue;
            }
            try {
                if (member.getSpectatorTarget() != target) {
                    member.setSpectatorTarget(target);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public void removeInstance(String instanceId) {
        states.remove(instanceId);
        warnedMarks.remove(instanceId);
        if (instanceId == null) {
            return;
        }
        World world = Bukkit.getWorld(instanceId);
        if (world == null) {
            return;
        }
        Collection<? extends Player> players = world.getPlayers();
        for (Player player : players) {
            spectators.remove(player.getUniqueId());
        }
    }

    public void forgetPlayer(UUID playerId) {
        if (playerId != null) {
            spectators.remove(playerId);
        }
    }
}
