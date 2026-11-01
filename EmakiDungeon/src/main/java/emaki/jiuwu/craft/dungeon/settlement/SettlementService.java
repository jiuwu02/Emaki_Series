package emaki.jiuwu.craft.dungeon.settlement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.action.ActionBridge;
import emaki.jiuwu.craft.dungeon.audit.AuditService;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.drop.DropAllocator;
import emaki.jiuwu.craft.dungeon.drop.RewardChestService;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.InstanceState;
import emaki.jiuwu.craft.dungeon.leaderboard.LeaderboardStore;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceCoordinator;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.party.Party;
import emaki.jiuwu.craft.dungeon.party.PartyService;
import emaki.jiuwu.craft.dungeon.recovery.RecoveryService;
import emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxService;
import emaki.jiuwu.craft.dungeon.score.ScoreCalculator;
import emaki.jiuwu.craft.dungeon.score.ScoreStats;
import emaki.jiuwu.craft.dungeon.score.ScoreTracker;
import emaki.jiuwu.craft.dungeon.script.ScriptRuntimeManager;
import emaki.jiuwu.craft.dungeon.snapshot.PlayerSnapshot;
import emaki.jiuwu.craft.dungeon.snapshot.SnapshotManager;
import emaki.jiuwu.craft.dungeon.trigger.TriggerType;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

public final class SettlementService {

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final InstanceRegistry registry;
    private final PartyService parties;
    private final ScoreTracker scoreTracker;
    private final SnapshotManager snapshots;
    private final RecoveryService recovery;
    private final RewardChestService rewardChests;
    private final LeaderboardStore leaderboard;
    private final InstanceCoordinator coordinator;
    private final SettlementCommandRunner commandRunner;
    private final RewardBoxService rewardBox;
    private final MessageService messages;
    private final DebugLogger debug;
    private final Function<String, DungeonConfig> configOf;

    private volatile ActionBridge actionBridge;
    private volatile ScriptRuntimeManager scriptManager;
    private volatile AuditService audit;

    public void bindAudit(AuditService audit) {
        this.audit = audit;
    }

    public void bindScriptManager(ScriptRuntimeManager scriptManager) {
        this.scriptManager = scriptManager;
    }

    private void fireTrigger(TriggerType type,
            String instanceId, String dungeonId, Map<String, String> context) {
        if (scriptManager != null) {
            scriptManager.fireTrigger(type, instanceId, dungeonId, context);
        }
    }

    public SettlementService(Plugin plugin,
            ExecutionDispatcher dispatcher,
            InstanceRegistry registry,
            PartyService parties,
            ScoreTracker scoreTracker,
            SnapshotManager snapshots,
            RecoveryService recovery,
            RewardChestService rewardChests,
            LeaderboardStore leaderboard,
            InstanceCoordinator coordinator,
            SettlementCommandRunner commandRunner,
            RewardBoxService rewardBox,
            MessageService messages,
            DebugLogger debug,
            Function<String, DungeonConfig> configOf) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.parties = parties;
        this.scoreTracker = scoreTracker;
        this.snapshots = snapshots;
        this.recovery = recovery;
        this.rewardChests = rewardChests;
        this.leaderboard = leaderboard;
        this.coordinator = coordinator;
        this.commandRunner = commandRunner;
        this.rewardBox = rewardBox;
        this.messages = messages;
        this.debug = debug;
        this.configOf = configOf;
    }

    public void bindActionBridge(ActionBridge actionBridge) {
        this.actionBridge = actionBridge;
    }

    public void beginComplete(DungeonInstance instance, String cause) {
        if (instance == null) {
            return;
        }
        boolean moved = instance.transition(InstanceState.RUNNING, InstanceState.SETTLING);
        debug.logRaw("dungeon", (UUID) null, "beginComplete | instance="
                + instance.instanceId() + " | cause=" + cause + " | transitioned=" + moved);
        if (!moved) {
            return;
        }
        dispatcher.runGlobal(plugin, () -> settle(instance, cause, null));
    }

    public boolean beginFail(DungeonInstance instance, String reason) {
        if (instance == null) {
            return false;
        }
        if (!instance.transition(InstanceState.RUNNING, InstanceState.SETTLING)) {
            return false;
        }
        dispatcher.runGlobal(plugin, () -> settle(instance, "fail:" + reason, reason));
        return true;
    }

    public void onEnding(DungeonInstance instance, String optionCase, String endingId) {
        if (instance == null) {
            return;
        }
        if (actionBridge != null) {
            actionBridge.dispatch("dungeon_ending", null, Map.of(
                    "instanceId", instance.instanceId(),
                    "endingId", endingId == null ? "" : endingId,
                    "optionCase", optionCase == null ? "" : optionCase
            ));
        }
        Bukkit.getPluginManager().callEvent(new emaki.jiuwu.craft.dungeon.api.event.DungeonEndingEvent(
                instance.instanceId(), instance.dungeonId(), endingId, optionCase));
        debug.logRaw("dungeon", (UUID) null,
                "ending reached | instance=" + instance.instanceId() + " | ending=" + endingId);
        beginComplete(instance, "ending:" + (endingId == null ? "" : endingId));
    }

    private void settle(DungeonInstance instance, String cause, String failReason) {
        String instanceId = instance.instanceId();
        DungeonConfig config = configOf.apply(instance.dungeonId());
        List<UUID> members = membersOf(instance);
        if (members.isEmpty()) {
            debug.logRaw("dungeon", (UUID) null,
                    "settle aborted | instance=" + instanceId + " | reason=no_members");
            coordinator.dispose(instanceId, failReason == null ? "settle:" + cause : cause);
            return;
        }
        Map<UUID, ScoreStats> combat = new LinkedHashMap<>();
        Map<UUID, Double> scores = new LinkedHashMap<>();
        double teamScore = 0.0D;
        long durationMs = instance.startedAt() == 0L
                ? 0L : System.currentTimeMillis() - instance.startedAt();
        if (config != null) {
            Map<UUID, ScoreStats> snapshot = new LinkedHashMap<>(scoreTracker.snapshot(instanceId));
            for (UUID memberId : members) {
                combat.put(memberId, snapshot.getOrDefault(memberId, ScoreStats.zero()));
            }
            scores = ScoreCalculator.calculate(config.scoreWeights(), combat);
            teamScore = scores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0D);
        }
        if (actionBridge != null && failReason == null) {
            for (UUID memberId : members) {
                ScoreStats stats = combat.getOrDefault(memberId, ScoreStats.zero());
                Double score = scores.get(memberId);
                actionBridge.dispatch("dungeon_score", memberId, Map.of(
                        "instanceId", instanceId,
                        "score", String.valueOf(score == null ? 0.0D : score),
                        "damage", String.valueOf((long) stats.damage()),
                        "kills", String.valueOf(stats.kills()),
                        "heal", String.valueOf((long) stats.heal()),
                        "death", String.valueOf(stats.death()),
                        "defense", String.valueOf((long) stats.defense())
                ));
            }
        }
        debug.logRaw("dungeon", (UUID) null,
                "score computed | instance=" + instanceId + " | team=" + teamScore);
        if (failReason == null) {
            fireTrigger(TriggerType.onScoreReady,
                    instanceId, instance.dungeonId(), Map.of(
                            "score", String.valueOf(teamScore),
                            "durationMs", String.valueOf(durationMs)));
        }
        for (UUID memberId : members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                continue;
            }
            PlayerSnapshot snapshot = snapshots.read(instanceId, memberId);
            if (snapshot != null) {
                snapshot.applyInventoryTo(member);
            }
        }
        if ("score".equals(instance.dropMode())) {
            allocateScoreDrops(instance, config, members, scores);
        } else {
            payoutStaging(instance, members);
        }
        SettlementCommandRunner.RunResult runResult =
                commandRunner.run(config == null ? List.of() : config.settlementCommands(),
                        members, instanceId);
        if (!runResult.success()) {
            for (UUID memberId : members) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    messages.send(member, "settle.partial", Map.of());
                }
            }
            debug.logRaw("dungeon", (UUID) null,
                    "settle_partial | instance=" + instanceId + " | steps=" + runResult.steps());
            fireTrigger(TriggerType.onSettlePartial,
                    instanceId, instance.dungeonId(), Map.of(
                            "steps", String.valueOf(runResult.steps())));
            if (audit != null) {
                audit.write("settle", Map.of(
                        "event", "partial",
                        "instance", instanceId,
                        "steps", String.valueOf(runResult.steps())));
            }
        }
        if (actionBridge != null) {
            actionBridge.dispatch("dungeon_reward", null, Map.of(
                    "instanceId", instanceId,
                    "success", String.valueOf(runResult.success()),
                    "steps", String.valueOf(runResult.steps())
            ));
        }
        for (UUID memberId : members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                continue;
            }
            snapshots.repack(member, instanceId);
        }
        if (actionBridge != null && failReason == null) {
            actionBridge.dispatch("dungeon_complete", null, Map.of(
                    "instanceId", instanceId,
                    "dungeonId", instance.dungeonId(),
                    "durationMs", String.valueOf(durationMs),
                    "score", String.valueOf(teamScore)
            ));
        }
        if (config != null && !instance.isTest() && failReason == null) {
            List<LeaderboardStore.MemberScore> boardMembers = new ArrayList<>();
            for (UUID memberId : members) {
                Player member = Bukkit.getPlayer(memberId);
                String name = member != null ? member.getName() : memberId.toString().substring(0, 8);
                boardMembers.add(new LeaderboardStore.MemberScore(name,
                        scores.getOrDefault(memberId, 0.0D)));
            }
            leaderboard.recordAsync(instance.dungeonId(), new LeaderboardStore.Entry(
                    instance.difficulty(),
                    durationMs,
                    Math.round(teamScore * 10.0D) / 10.0D,
                    boardMembers,
                    System.currentTimeMillis()
            ), config.leaderboardMaxEntries());
        }
        debug.logRaw("dungeon", (UUID) null,
                "instance completed | instance=" + instanceId
                        + " | cause=" + cause
                        + " | duration_ms=" + durationMs);
        if (audit != null) {
            if (failReason == null) {
                audit.write("settle", Map.of(
                        "event", "complete",
                        "instance", instanceId,
                        "dungeon", instance.dungeonId(),
                        "cause", cause,
                        "score", String.valueOf(teamScore),
                        "duration_ms", String.valueOf(durationMs),
                        "test", String.valueOf(instance.isTest())));
            } else {
                audit.write("settle", Map.of(
                        "event", "fail",
                        "instance", instanceId,
                        "dungeon", instance.dungeonId(),
                        "cause", cause,
                        "reason", failReason,
                        "score", String.valueOf(teamScore),
                        "duration_ms", String.valueOf(durationMs),
                        "test", String.valueOf(instance.isTest())));
            }
        }
        if (failReason == null) {
            Bukkit.getPluginManager().callEvent(
                    new emaki.jiuwu.craft.dungeon.api.event.DungeonCompleteEvent(
                            instanceId, instance.dungeonId(), teamScore, durationMs));
        }
        for (UUID memberId : members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                continue;
            }
            messages.send(member, "settle.rewards_hint", Map.of());
        }
        for (UUID memberId : members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                continue;
            }
            recovery.onExit(member, failReason == null ? "complete" : cause);
        }
        coordinator.dispose(instanceId, failReason == null ? "complete:" + cause : cause);
    }

    private void payoutStaging(DungeonInstance instance, List<UUID> members) {
        Map<UUID, List<ItemStack>> staged =
                rewardChests.drainStaging(instance.instanceId());
        if (staged.isEmpty()) {
            return;
        }
        World instanceWorld = Bukkit.getWorld(instance.instanceId());
        for (UUID memberId : members) {
            List<ItemStack> items = staged.remove(memberId);
            if (items == null || items.isEmpty()) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                rewardBox.storeBox(memberId, items);
                continue;
            }
            List<ItemStack> leftover = rewardBox.deposit(memberId, items);
            if (!leftover.isEmpty()) {
                Location fallback = instanceWorld == null
                        ? member.getLocation()
                        : instanceWorld.getSpawnLocation();
                rewardBox.deliverOrDrop(memberId, leftover, instanceWorld, fallback);
            }
            messages.send(member, "drop.payout_done", Map.of(
                    "count", String.valueOf(items.size())));
        }
    }

    private void allocateScoreDrops(DungeonInstance instance,
            DungeonConfig config,
            List<UUID> members,
            Map<UUID, Double> scores) {
        String instanceId = instance.instanceId();
        List<ItemStack> pool = rewardChests.drainPool(instanceId);
        rewardChests.clearVisual(instance);
        if (pool.isEmpty()) {
            return;
        }
        Map<UUID, List<ItemStack>> allocation = DropAllocator.allocate(
                pool, scores, config == null ? Map.of() : config.dropLevels(), members);
        for (Map.Entry<UUID, List<ItemStack>> entry : allocation.entrySet()) {
            List<ItemStack> leftover =
                    rewardBox.deposit(entry.getKey(), entry.getValue());
            if (!leftover.isEmpty()) {
                World world = Bukkit.getWorld(instanceId);
                rewardBox.deliverOrDrop(entry.getKey(), leftover, world,
                        world == null ? null : world.getSpawnLocation());
            }
        }
        debug.logRaw("dungeon", (UUID) null,
                "score_drops_allocated | instance=" + instanceId
                        + " | stacks=" + pool.size());
    }

    private List<UUID> membersOf(DungeonInstance instance) {
        UUID partyId = instance.partyId();
        Party party = partyId == null ? null : parties.partyById(partyId);
        if (party != null) {
            return new ArrayList<>(party.members());
        }
        World world = Bukkit.getWorld(instance.instanceId());
        if (world == null) {
            return List.of();
        }
        List<UUID> members = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            members.add(player.getUniqueId());
        }
        return members;
    }
}
