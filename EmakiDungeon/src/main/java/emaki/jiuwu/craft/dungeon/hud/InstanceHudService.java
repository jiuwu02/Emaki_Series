package emaki.jiuwu.craft.dungeon.hud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.InstanceState;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;

public final class InstanceHudService {

    private static final long TICK_PERIOD = 20L;
    private static final String OBJECTIVE_NAME = "emd_hud";

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final InstanceRegistry registry;
    private final Function<String, DungeonConfig> configOf;
    private final LanguageLoader lang;
    private final Map<String, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private TaskToken tickTask = TaskToken.UNAVAILABLE;

    public InstanceHudService(Plugin plugin, ExecutionDispatcher dispatcher,
            InstanceRegistry registry, Function<String, DungeonConfig> configOf,
            LanguageLoader lang) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.configOf = configOf;
        this.lang = lang;
    }

    public void start() {
        tickTask = dispatcher.runGlobalTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public void shutdown() {
        tickTask.cancel();
        tickTask = TaskToken.UNAVAILABLE;
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
        for (UUID playerId : List.copyOf(boards.keySet())) {
            restoreScoreboard(playerId);
        }
        boards.clear();
    }

    private void tick() {
        Set<String> activeBars = new HashSet<>();
        Set<UUID> activeBoards = new HashSet<>();
        for (DungeonInstance instance : registry.all()) {
            InstanceState state = instance.state();
            if (state == InstanceState.DISPOSING || state == InstanceState.DISPOSED) {
                continue;
            }
            DungeonConfig config = configOf.apply(instance.dungeonId());
            if (config == null || !config.hudEnabled()) {
                continue;
            }
            World world = Bukkit.getWorld(instance.instanceId());
            if (world == null) {
                continue;
            }
            List<Player> players = List.copyOf(world.getPlayers());
            if (players.isEmpty()) {
                continue;
            }
            if ("scoreboard".equals(config.hudChannel())) {
                for (Player player : players) {
                    activeBoards.add(player.getUniqueId());
                    updateScoreboard(instance, player);
                }
            } else {
                activeBars.add(instance.instanceId());
                updateBossBar(instance, players);
            }
        }
        for (String instanceId : List.copyOf(bars.keySet())) {
            if (!activeBars.contains(instanceId)) {
                BossBar bar = bars.remove(instanceId);
                if (bar != null) {
                    bar.removeAll();
                }
            }
        }
        for (UUID playerId : List.copyOf(boards.keySet())) {
            if (!activeBoards.contains(playerId)) {
                restoreScoreboard(playerId);
                boards.remove(playerId);
            }
        }
    }

    private void updateBossBar(DungeonInstance instance, List<Player> players) {
        String title = render(instance, players.get(0), lang.getMessage("hud.bossbar_title"));
        BossBar bar = bars.get(instance.instanceId());
        if (bar == null) {
            bar = Bukkit.createBossBar(MiniMessages.plainText(title), BarColor.BLUE, BarStyle.SOLID);
            bar.setProgress(1.0D);
            bars.put(instance.instanceId(), bar);
        } else {
            bar.setTitle(MiniMessages.plainText(title));
        }
        List<Player> attached = List.copyOf(bar.getPlayers());
        for (Player player : attached) {
            if (!players.contains(player)) {
                bar.removePlayer(player);
            }
        }
        for (Player player : players) {
            if (!attached.contains(player)) {
                bar.addPlayer(player);
            }
        }
    }

    private void updateScoreboard(DungeonInstance instance, Player player) {
        String title = render(instance, player, lang.getMessage("hud.scoreboard_title"));
        List<String> lines = new ArrayList<>();
        for (String template : Texts.asStringList(lang.getValue("hud.scoreboard_lines"))) {
            lines.add(render(instance, player, template));
        }
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            Scoreboard created = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = created.registerNewObjective(
                    OBJECTIVE_NAME, Criteria.DUMMY, MiniMessages.parse(title));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            player.setScoreboard(created);
            boards.put(player.getUniqueId(), created);
            board = created;
        } else {
            Objective objective = board.getObjective(OBJECTIVE_NAME);
            if (objective != null) {
                objective.displayName(MiniMessages.parse(title));
            }
        }
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            return;
        }
        for (String entry : new ArrayList<>(board.getEntries())) {
            board.resetScores(entry);
        }
        int score = lines.size();
        for (String line : lines) {
            objective.getScore(MiniMessages.plainText(line)).setScore(score--);
        }
    }

    private String render(DungeonInstance instance, Player viewer, String template) {
        String internal = Texts.formatTemplate(template, Map.of(
                "dungeon", instance.dungeonId(),
                "difficulty", instance.difficulty(),
                "type", instance.type()));
        return PlaceholderRenderer.renderPapi(viewer, internal, null, "dungeon.hud");
    }

    private void restoreScoreboard(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }
}
