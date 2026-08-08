package emaki.jiuwu.craft.station.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.station.EmakiStationPlugin;
import emaki.jiuwu.craft.station.api.model.QueueEntryView;
import emaki.jiuwu.craft.station.api.model.QueueSnapshot;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.dismantle.DismantleStationDefinition;
import emaki.jiuwu.craft.station.gui.DurationDisplay;
import emaki.jiuwu.craft.station.queue.PlayerQueues;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;

/**
 * Routes {@code /emakistation} subcommands.
 *
 * <p>Every subcommand that touches a player's window or inventory dispatches to that player's owner thread
 * first, because a command can arrive from the console or from another player's thread.
 */
public final class StationCommandRouter {

    private static final String PERMISSION_ROOT = "emakistation";
    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";
    private static final String PERMISSION_DISMANTLE = PERMISSION_ROOT + ".dismantle";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";
    private static final String PERMISSION_ACCESS_PREFIX = PERMISSION_ROOT + ".access.";
    private static final String PERMISSION_DISMANTLE_ACCESS_PREFIX = PERMISSION_ROOT + ".dismantle.access.";
    private static final List<String> SUBCOMMANDS =
            List.of("help", "open", "dismantle", "queue", "claim", "cancel", "list", "reload", "debug", "admin");

    private final EmakiStationPlugin plugin;

    /**
     * Creates the router.
     *
     * @param plugin the owning plugin
     */
    public StationCommandRouter(EmakiStationPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles one command invocation.
     *
     * @param sender the caller
     * @param label  the root label used
     * @param args   the raw arguments
     * @return always {@code true}; failures are reported as messages, not usage errors
     */
    public boolean onCommand(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "open" -> handleOpen(sender, args);
            case "dismantle" -> handleDismantle(sender, args);
            case "queue" -> handleQueue(sender);
            case "claim" -> handleClaim(sender);
            case "cancel" -> handleCancel(sender, args);
            case "list" -> handleList(sender, args);
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender, args);
            case "admin" -> handleAdmin(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    /**
     * Suggests completions.
     *
     * @param sender the caller
     * @param args   the raw arguments
     * @return the suggestions
     */
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    result.add(sub);
                }
            }
            return result;
        }
        String head = args[0].toLowerCase(Locale.ROOT);
        if ("debug".equals(head)) {
            return plugin.debugCommand() == null
                    ? List.of()
                    : plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        if ("dismantle".equals(head) && args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (DismantleStationDefinition station : plugin.dismantleRegistry().all()) {
                if (station.id().startsWith(prefix)) {
                    result.add(station.id());
                }
            }
            return result;
        }
        boolean stationArgument = "open".equals(head) || "cancel".equals(head) || "list".equals(head);
        if (stationArgument && args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (StationDefinition station : plugin.registry().stations()) {
                if (station.id().startsWith(prefix)) {
                    result.add(station.id());
                }
            }
        }
        return result;
    }

    private boolean handleDismantle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_DISMANTLE)) {
            message(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2) {
            message(sender, "command.dismantle_usage");
            return true;
        }
        DismantleStationDefinition station = plugin.dismantleRegistry().find(args[1]);
        if (station == null) {
            message(sender, "command.unknown_station", Map.of("station", args[1]));
            return true;
        }
        String required =
                station.effectivePermission(PERMISSION_DISMANTLE_ACCESS_PREFIX + station.id());
        if (!required.isBlank() && !player.hasPermission(required)) {
            message(sender, "general.no_permission");
            return true;
        }
        plugin.executionDispatcher().runEntity(plugin, player, () -> {
            EmakiResult<Unit> result = plugin.stationGuiService().openDismantle(player, station.id());
            if (result.isFailure()) {
                message(player, "command.dismantle_failed", Map.of("reason", result.reasonKey()));
            }
        }, () -> {
            // The player left before the window could open; nothing to clean up.
        });
        return true;
    }

    private boolean handleOpen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_USE)) {
            message(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2) {
            message(sender, "command.open_usage");
            return true;
        }
        StationDefinition station = plugin.registry().station(args[1]);
        if (station == null) {
            message(sender, "command.unknown_station", Map.of("station", args[1]));
            return true;
        }
        String required = station.effectivePermission(PERMISSION_ACCESS_PREFIX + station.id());
        if (!required.isBlank() && !player.hasPermission(required)) {
            message(sender, "general.no_permission");
            return true;
        }
        plugin.executionDispatcher().runEntity(plugin, player, () -> {
            EmakiResult<Unit> result = plugin.stationGuiService().open(player, station.id());
            if (result.isFailure()) {
                message(player, "command.open_failed", Map.of("reason", result.reasonKey()));
            }
        }, () -> {
            // The player left before the window could open; nothing to clean up.
        });
        return true;
    }

    private boolean handleQueue(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_USE)) {
            message(sender, "general.no_permission");
            return true;
        }
        PlayerQueues queues = plugin.queueService().cached(player.getUniqueId());
        if (queues == null || queues.isEmpty()) {
            message(sender, "command.queue_empty");
            return true;
        }
        for (String stationId : queues.stationIds()) {
            StationDefinition station = plugin.registry().station(stationId);
            if (station == null) {
                continue;
            }
            QueueSnapshot snapshot = plugin.queueService().snapshot(player, station, queues);
            message(sender, "command.queue_header", Map.of(
                    "station", station.displayName(),
                    "used", String.valueOf(snapshot.occupiedLength()),
                    "max", String.valueOf(snapshot.maxLength())));
            for (QueueEntryView entry : snapshot.entries()) {
                message(sender, "command.queue_entry", Map.of(
                        "index", String.valueOf(entry.index() + 1),
                        "recipe", entry.recipeId(),
                        "batch", String.valueOf(entry.batch()),
                        "state", entry.state().token(),
                        "remaining", DurationDisplay.format(entry.remainingMillis())));
            }
        }
        return true;
    }

    private boolean handleClaim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_USE)) {
            message(sender, "general.no_permission");
            return true;
        }
        plugin.executionDispatcher().runEntity(plugin, player,
                () -> plugin.craftService().claimAsync(player).thenAccept(result -> {
                    if (result.isFailure()) {
                        message(player, "command.claim_failed", Map.of("reason", result.reasonKey()));
                        return;
                    }
                    message(player, "command.claim_done",
                            Map.of("count", String.valueOf(result.orElse(0))));
                }), () -> {
                    // The player left before the claim ran; their pending outputs stay pending.
                });
        return true;
    }

    private boolean handleCancel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_USE)) {
            message(sender, "general.no_permission");
            return true;
        }
        if (args.length < 3) {
            message(sender, "command.cancel_usage");
            return true;
        }
        StationDefinition station = plugin.registry().station(args[1]);
        if (station == null) {
            message(sender, "command.unknown_station", Map.of("station", args[1]));
            return true;
        }
        int index;
        try {
            index = Integer.parseInt(args[2]) - 1;
        } catch (NumberFormatException invalid) {
            message(sender, "command.cancel_usage");
            return true;
        }
        int target = index;
        plugin.executionDispatcher().runEntity(plugin, player,
                () -> plugin.craftService().cancelAsync(player, station, target).thenAccept(result -> {
                    if (result.isFailure()) {
                        message(player, "command.cancel_failed", Map.of("reason", result.reasonKey()));
                        return;
                    }
                    message(player, "command.cancel_done", Map.of("index", String.valueOf(target + 1)));
                }), () -> {
                    // The player left before the cancellation ran; the entry stays queued.
                });
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_USE)) {
            message(sender, "general.no_permission");
            return true;
        }
        if (args.length >= 2) {
            StationDefinition station = plugin.registry().station(args[1]);
            if (station == null) {
                message(sender, "command.unknown_station", Map.of("station", args[1]));
                return true;
            }
            for (RecipeDefinition recipe : plugin.registry().recipesOf(station.id())) {
                message(sender, "command.list_recipe", Map.of(
                        "recipe", recipe.id(),
                        "name", recipe.displayName(),
                        "duration", String.valueOf(recipe.durationSeconds())));
            }
            return true;
        }
        for (StationDefinition station : plugin.registry().stations()) {
            message(sender, "command.list_station", Map.of(
                    "station", station.id(),
                    "name", station.displayName(),
                    "recipes", String.valueOf(plugin.registry().recipeIdsOf(station.id()).size())));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            message(sender, "general.no_permission");
            return true;
        }
        EmakiStationPlugin.ReloadSummary summary = plugin.reloadContent();
        message(sender, "command.reload_done", Map.of(
                "stations", String.valueOf(summary.stations()),
                "recipes", String.valueOf(summary.recipes()),
                "issues", String.valueOf(summary.issues())));
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DEBUG)) {
            message(sender, "general.no_permission");
            return true;
        }
        if (plugin.debugCommand() == null) {
            message(sender, "command.debug_unavailable");
            return true;
        }
        plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length),
                plugin.messageService());
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            message(sender, "general.no_permission");
            return true;
        }
        if (args.length < 3 || !"queue".equalsIgnoreCase(args[1])) {
            message(sender, "command.admin_usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[2]);
        UUID targetId = target == null ? null : target.getUniqueId();
        if (targetId == null) {
            message(sender, "command.unknown_player", Map.of("player", args[2]));
            return true;
        }
        plugin.queueService().loadAsync(targetId).thenAccept(queues -> {
            if (queues == null || queues.isEmpty()) {
                message(sender, "command.queue_empty");
                return;
            }
            for (String stationId : queues.stationIds()) {
                StationDefinition station = plugin.registry().station(stationId);
                if (station == null) {
                    continue;
                }
                QueueSnapshot snapshot = plugin.queueService().snapshot(targetId, station, queues);
                message(sender, "command.queue_header", Map.of(
                        "station", station.displayName(),
                        "used", String.valueOf(snapshot.occupiedLength()),
                        "max", String.valueOf(snapshot.maxLength())));
                for (QueueEntryView entry : snapshot.entries()) {
                    message(sender, "command.queue_entry", Map.of(
                            "index", String.valueOf(entry.index() + 1),
                            "recipe", entry.recipeId(),
                            "batch", String.valueOf(entry.batch()),
                            "state", entry.state().token(),
                            "remaining", DurationDisplay.format(entry.remainingMillis())));
                }
            }
        });
        return true;
    }

    private void sendHelp(CommandSender sender) {
        message(sender, "command.help");
    }

    private void message(CommandSender sender, String key) {
        if (plugin.messageService() != null) {
            plugin.messageService().send(sender, key);
        }
    }

    private void message(CommandSender sender, String key, Map<String, ?> replacements) {
        if (plugin.messageService() != null) {
            plugin.messageService().send(sender, key, replacements);
        }
    }
}
