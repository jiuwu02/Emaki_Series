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
import emaki.jiuwu.craft.dungeon.score.ScoreCalculator;
import emaki.jiuwu.craft.dungeon.score.ScoreStats;
import emaki.jiuwu.craft.dungeon.score.ScoreTracker;
import emaki.jiuwu.craft.dungeon.snapshot.PlayerSnapshot;
import emaki.jiuwu.craft.dungeon.snapshot.SnapshotManager;

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
    private final emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxService rewardBox;
    private final MessageService messages;
    private final DebugLogger debug;
    private final java.util.function.Function<String, DungeonConfig> configOf;

    private volatile emaki.jiuwu.craft.dungeon.action.ActionBridge actionBridge;

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
            emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxService rewardBox,
            MessageService messages,
            DebugLogger debug,
            java.util.function.Function<String, DungeonConfig> configOf) {
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

    public void bindActionBridge(emaki.jiuwu.craft.dungeon.action.ActionBridge actionBridge) {
        this.actionBridge = actionBridge;
    }

    public void beginComplete(DungeonInstance instance, String cause) {
        if (instance == null) {
            return;
        }
        if (!instance.transition(InstanceState.RUNNING, InstanceState.SETTLING)) {
            return;
        }
        dispatcher.runGlobal(plugin, () -> settle(instance, cause));
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

    private void settle(DungeonInstance instance, String cause) {
        String instanceId = instance.instanceId();
        DungeonConfig config = configOf.apply(instance.dungeonId());
        List<UUID> members = membersOf(instance);
        if (members.isEmpty()) {
            debug.logRaw("dungeon", (UUID) null,
                    "settle aborted | instance=" + instanceId + " | reason=no_members");
            coordinator.dispose(instanceId, "settle:" + cause);
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
        if (actionBridge != null) {
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
        if (config != null && config.inventoryIsolation()) {
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
        }
        if ("score".equals(instance.dropMode())) {
            allocateScoreDrops(instance, config, members, scores);
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
        if (actionBridge != null) {
            actionBridge.dispatch("dungeon_complete", null, Map.of(
                    "instanceId", instanceId,
                    "dungeonId", instance.dungeonId(),
                    "durationMs", String.valueOf(durationMs),
                    "score", String.valueOf(teamScore)
            ));
        }
        if (config != null) {
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
        Bukkit.getPluginManager().callEvent(
                new emaki.jiuwu.craft.dungeon.api.event.DungeonCompleteEvent(
                        instanceId, instance.dungeonId(), teamScore, durationMs));
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
            recovery.onExit(member, "complete");
        }
        coordinator.dispose(instanceId, "complete:" + cause);
    }

    private void allocateScoreDrops(DungeonInstance instance,
            DungeonConfig config,
            List<UUID> members,
            Map<UUID, Double> scores) {
        String instanceId = instance.instanceId();
        List<org.bukkit.inventory.ItemStack> pool = rewardChests.drainPool(instanceId);
        rewardChests.clearVisual(instance);
        if (pool.isEmpty()) {
            return;
        }
        Map<UUID, List<org.bukkit.inventory.ItemStack>> allocation = DropAllocator.allocate(
                pool, scores, config == null ? Map.of() : config.dropLevels(), members);
        for (Map.Entry<UUID, List<org.bukkit.inventory.ItemStack>> entry : allocation.entrySet()) {
            List<org.bukkit.inventory.ItemStack> leftover =
                    rewardBox.deposit(entry.getKey(), entry.getValue());
            if (!leftover.isEmpty()) {
                org.bukkit.World world = Bukkit.getWorld(instanceId);
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
        org.bukkit.World world = Bukkit.getWorld(instance.instanceId());
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
