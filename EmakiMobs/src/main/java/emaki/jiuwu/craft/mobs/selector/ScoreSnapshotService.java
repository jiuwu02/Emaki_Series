package emaki.jiuwu.craft.mobs.selector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;

public final class ScoreSnapshotService implements Listener, AutoCloseable {

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final Supplier<TargetSelectorConfig> configSupplier;
    private final EquipmentScorer equipmentScorer;
    private final ExpressionScorer expressionScorer;
    private final Map<UUID, Player> players = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerScoreSnapshot> snapshots = new ConcurrentHashMap<>();
    private final AtomicInteger cursor = new AtomicInteger();
    private final AtomicLong generation = new AtomicLong();

    private volatile TaskToken refreshTask;

    public ScoreSnapshotService(Plugin plugin,
            ExecutionDispatcher dispatcher,
            Supplier<TargetSelectorConfig> configSupplier,
            ItemSourceService itemSourceService,
            Supplier<DebugLogger> debugLoggerSupplier) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.configSupplier = configSupplier;
        this.equipmentScorer = new EquipmentScorer(itemSourceService);
        this.expressionScorer = new ExpressionScorer(debugLoggerSupplier);
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.put(player.getUniqueId(), player);
        }
    }

    @Nullable
    public PlayerScoreSnapshot snapshot(UUID playerId) {
        return snapshots.get(playerId);
    }

    public boolean isTracked(UUID playerId) {
        return players.containsKey(playerId);
    }

    public void reload() {
        TaskToken currentTask = refreshTask;
        if (currentTask != null) {
            currentTask.cancel();
        }
        refreshTask = null;
        snapshots.clear();
        cursor.set(0);
        expressionScorer.resetFailures();
        long currentGeneration = generation.incrementAndGet();
        TargetSelectorConfig config = configSupplier.get();
        if (config == null || config.snapshotIntervalTicks() <= 0) {
            return;
        }
        long interval = config.snapshotIntervalTicks();
        refreshTask = dispatcher.runGlobalTimer(plugin,
                () -> scheduleBatch(currentGeneration), interval, interval);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        players.put(player.getUniqueId(), player);
        long currentGeneration = generation.get();
        dispatcher.runEntity(plugin, player,
                () -> recompute(player, currentGeneration),
                () -> removeIfGeneration(player.getUniqueId(), currentGeneration));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        generation.incrementAndGet();
        TaskToken currentTask = refreshTask;
        if (currentTask != null) {
            currentTask.cancel();
        }
        refreshTask = null;
        players.clear();
        snapshots.clear();
        expressionScorer.resetFailures();
    }

    private void scheduleBatch(long expectedGeneration) {
        if (expectedGeneration != generation.get()) {
            return;
        }
        TargetSelectorConfig config = configSupplier.get();
        if (config == null || config.snapshotIntervalTicks() <= 0) {
            return;
        }
        List<Player> online = new ArrayList<>(players.values());
        if (online.isEmpty()) {
            return;
        }
        int count = Math.min(config.snapshotPlayersPerTick(), online.size());
        int start = Math.floorMod(cursor.getAndAdd(count), online.size());
        for (int index = 0; index < count; index++) {
            Player player = online.get((start + index) % online.size());
            dispatcher.runEntity(plugin, player,
                    () -> recompute(player, expectedGeneration),
                    () -> removeIfGeneration(player.getUniqueId(), expectedGeneration));
        }
    }

    private void recompute(Player player, long expectedGeneration) {
        if (expectedGeneration != generation.get()) {
            return;
        }
        if (!player.isOnline()) {
            removeIfGeneration(player.getUniqueId(), expectedGeneration);
            return;
        }
        TargetSelectorConfig config = configSupplier.get();
        if (config == null || config.snapshotIntervalTicks() <= 0) {
            snapshots.remove(player.getUniqueId());
            return;
        }
        Map<String, Double> equipmentScores = new HashMap<>();
        for (String tableId : config.referencedEquipmentTables()) {
            EquipmentWeightTable table = config.equipmentTables().get(tableId);
            if (table != null) {
                equipmentScores.put(tableId, equipmentScorer.score(player, table));
            }
        }
        Map<String, Double> expressionScores = new HashMap<>();
        config.expressions().forEach((expressionId, expression) -> expressionScores.put(
                expressionId, expressionScorer.score(player, expressionId, expression)));
        Map<String, Boolean> filterResults = new HashMap<>();
        config.selectors().forEach((selectorId, definition) -> {
            if (definition.filter() != null && definition.filter().configured()) {
                filterResults.put(selectorId, ConditionEvaluator.evaluate(
                        definition.filter(),
                        text -> PlaceholderRenderer.renderPapi(
                                player, text, null, "mob_target_selector_filter"),
                        ConditionContext.of(player)));
            }
        });
        var location = player.getLocation();
        PlayerScoreSnapshot snapshot = new PlayerScoreSnapshot(
                equipmentScores, expressionScores, filterResults,
                location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(),
                player.getHealth(), System.currentTimeMillis());
        if (expectedGeneration == generation.get()) {
            snapshots.put(player.getUniqueId(), snapshot);
        }
    }

    private void removeIfGeneration(UUID playerId, long expectedGeneration) {
        if (expectedGeneration == generation.get()) {
            remove(playerId);
        }
    }

    private void remove(UUID playerId) {
        players.remove(playerId);
        snapshots.remove(playerId);
    }
}
