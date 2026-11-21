package emaki.jiuwu.craft.station.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.station.EmakiStationPlugin;
import emaki.jiuwu.craft.station.api.model.QueueEntryView;
import emaki.jiuwu.craft.station.api.model.QueueSnapshot;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.dismantle.DismantleStationDefinition;
import emaki.jiuwu.craft.station.gui.DurationDisplay;
import emaki.jiuwu.craft.station.queue.PlayerQueues;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;
import emaki.jiuwu.craft.corelib.api.command.CommandKeys;
import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport.Guard;

public final class StationCommandRouter {

    private static final String PERMISSION_ROOT = "emakistation";
    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";
    private static final String PERMISSION_DISMANTLE = PERMISSION_ROOT + ".dismantle";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";
    private static final String PERMISSION_EDIT = PERMISSION_ROOT + ".edit";
    private static final String PERMISSION_ACCESS_PREFIX = PERMISSION_ROOT + ".access.";
    private static final String PERMISSION_DISMANTLE_ACCESS_PREFIX = PERMISSION_ROOT + ".dismantle.access.";
    private static final List<String> SUBCOMMANDS =
            List.of("help", "open", "dismantle", "queue", "claim", "cancel", "list", "edit",
                    "reload", "debug", "admin");

    private final EmakiStationPlugin plugin;

    public StationCommandRouter(EmakiStationPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "open" -> handleOpen(sender, args);
            case "dismantle" -> handleDismantle(sender, args);
            case "queue" -> handleQueue(sender);
            case "claim" -> handleClaim(sender);
            case "cancel" -> handleCancel(sender, args);
            case "list" -> handleList(sender, args);
            case "edit" -> handleEdit(sender, args);
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender, args);
            case "admin" -> handleAdmin(sender, args);
            case "help" -> {
                sendHelpPage(sender, helpRoot(label), Arrays.asList(args).subList(1, args.length));
                yield true;
            }
            default -> {
                sendHelp(sender, label);
                yield true;
            }
        };
    }

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length <= 1) {
            return CommandTabHelper.completeSubcommands(SUBCOMMANDS,
                    args.length == 0 ? "" : args[0]);
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
        if ("edit".equals(head) && args.length == 2 && plugin.editorModule() != null) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String id : plugin.editorModule().knownIds()) {
                if (id.startsWith(prefix)) {
                    result.add(id);
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
        if ("cancel".equals(head) && args.length == 3 && sender instanceof Player player) {
            StationDefinition station = plugin.registry().station(args[1]);
            if (station != null) {
                PlayerQueues queues = plugin.queueService().cached(player.getUniqueId());
                if (queues != null) {
                    QueueSnapshot snapshot = plugin.queueService().snapshot(player, station, queues);
                    String prefix = args[2].toLowerCase(Locale.ROOT);
                    for (QueueEntryView entry : snapshot.entries()) {
                        String value = String.valueOf(entry.index() + 1);
                        if (value.startsWith(prefix)) {
                            result.add(value);
                        }
                    }
                }
            }
        }
        if ("admin".equals(head)) {
            if (args.length == 2) {
                if ("queue".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add("queue");
                }
                return result;
            }
            if (args.length == 3 && "queue".equals(args[1].toLowerCase(Locale.ROOT))) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        result.add(online.getName());
                    }
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
            message(sender, CommandKeys.NO_PERMISSION);
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
            message(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        plugin.executionDispatcher().runEntity(plugin, player, () -> {
            EmakiResult<Unit> result = plugin.stationGuiService().openDismantle(player, station.id());
            if (result.isFailure()) {
                message(player, "command.dismantle_failed", Map.of("reason", result.reasonKey()));
            }
        }, () -> {

        });
        return true;
    }

    private boolean handleOpen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_USE)) {
            message(sender, CommandKeys.NO_PERMISSION);
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
            message(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        plugin.executionDispatcher().runEntity(plugin, player, () -> {
            EmakiResult<Unit> result = plugin.stationGuiService().open(player, station.id());
            if (result.isFailure()) {
                message(player, "command.open_failed", Map.of("reason", result.reasonKey()));
            }
        }, () -> {

        });
        return true;
    }

    private boolean handleQueue(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_USE)) {
            message(sender, CommandKeys.NO_PERMISSION);
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
            message(sender, CommandKeys.NO_PERMISSION);
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

                });
        return true;
    }

    private boolean handleCancel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_USE)) {
            message(sender, CommandKeys.NO_PERMISSION);
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

                });
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_USE)) {
            message(sender, CommandKeys.NO_PERMISSION);
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

    private boolean handleEdit(CommandSender sender, String[] args) {
        EditorCommandSupport.Guard editGuard = EditorCommandSupport.check(
                sender, () -> plugin.editorService() != null && plugin.editorModule() != null, PERMISSION_EDIT);
        if (editGuard == Guard.DENIED) {
            message(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (editGuard == Guard.NON_PLAYER) {
            message(sender, "general.players_only");
            return true;
        }
        if (editGuard == Guard.UNAVAILABLE) {
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            plugin.editorService().openBrowser(player);
            return true;
        }
        if (plugin.editorModule().openById(player, args[1]) == null) {
            message(sender, "editor.open_failed", Map.of("id", Texts.normalizeId(args[1])));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            message(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        long startTime = System.currentTimeMillis();
        EmakiStationPlugin.ReloadSummary summary = plugin.reloadContent();
        long elapsedMs = System.currentTimeMillis() - startTime;
        message(sender, "command.reload_done", Map.of(
                "stations", String.valueOf(summary.stations()),
                "recipes", String.valueOf(summary.recipes()),
                "issues", String.valueOf(summary.issues())));
        message(sender, "command.reload_elapsed", Map.of("elapsed", String.valueOf(elapsedMs)));
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DEBUG)) {
            message(sender, CommandKeys.NO_PERMISSION);
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
            message(sender, CommandKeys.NO_PERMISSION);
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

    private void sendHelp(CommandSender sender, String label) {
        sendHelpPage(sender, helpRoot(label), List.of());
    }

    private String helpRoot(String label) {
        return "/" + (label == null || label.isBlank() ? "emakistation" : label);
    }

    private void sendHelpPage(CommandSender sender, String root, List<String> path) {
        if (path.isEmpty()) {
            sendHelpLines(sender, root, "command.help_header",
                    "command.help_entry_open", "command.help_entry_dismantle", "command.help_entry_queue",
                    "command.help_entry_claim", "command.help_entry_cancel", "command.help_entry_list",
                    "command.help_entry_edit", "command.help_entry_reload", "command.help_entry_debug",
                    "command.help_entry_admin", "command.help_entry_help");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(0).toLowerCase(Locale.ROOT)) {
            case "open" -> sendIdHelp(sender, root, "open",
                    "command.help_open_header", "command.help_value_desc_open", stationIds());
            case "dismantle" -> sendIdHelp(sender, root, "dismantle",
                    "command.help_dismantle_header", "command.help_value_desc_dismantle", dismantleIds());
            case "cancel" -> sendCancelHelp(sender, root);
            case "admin" -> sendAdminHelp(sender, root, path);
            case "debug" -> sendDebugHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of());
        }
    }

    private List<String> stationIds() {
        List<String> ids = new ArrayList<>();
        for (StationDefinition station : plugin.registry().stations()) {
            ids.add(station.id());
        }
        return ids;
    }

    private List<String> dismantleIds() {
        List<String> ids = new ArrayList<>();
        for (DismantleStationDefinition station : plugin.dismantleRegistry().all()) {
            ids.add(station.id());
        }
        return ids;
    }

    private void sendIdHelp(CommandSender sender, String root, String command,
            String headerKey, String descKey, List<String> ids) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message(headerKey, Map.of("root", root)));
        List<String> values = new ArrayList<>(ids);
        values.sort(String::compareTo);
        if (values.isEmpty()) {
            sendHelpLine(sender, messageService.message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String value : values) {
            sendValueEntry(sender, value,
                    messageService.message(descKey, Map.of("value", value)),
                    root + " " + command + " " + value);
        }
        sendHelpFooter(sender);
    }

    private void sendCancelHelp(CommandSender sender, String root) {
        sendHelpLines(sender, root, "command.help_cancel_header",
                "command.help_cancel_entry_station", "command.help_cancel_entry_index");
        sendHelpFooter(sender);
    }

    private void sendAdminHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 2 && "queue".equalsIgnoreCase(path.get(1))) {
            sendPlayerHelp(sender, root, "admin queue",
                    "command.help_admin_queue_header", "command.help_value_desc_admin_queue");
            return;
        }
        sendHelpLines(sender, root, "command.help_admin_header", "command.help_admin_entry_queue");
        sendHelpFooter(sender);
    }

    private void sendDebugHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() < 2) {
            sendHelpLines(sender, root, "command.help_debug_header",
                    "command.help_debug_entry_status", "command.help_debug_entry_on",
                    "command.help_debug_entry_off", "command.help_debug_entry_player",
                    "command.help_debug_entry_module", "command.help_debug_entry_action");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(1).toLowerCase(Locale.ROOT)) {
            case "player" -> sendPlayerHelp(sender, root, "debug player",
                    "command.help_debug_player_header", "command.help_value_desc_debug_player");
            case "module" -> sendModuleHelp(sender, root);
            case "action" -> sendDebugActionHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of("debug"));
        }
    }

    private void sendPlayerHelp(CommandSender sender, String root, String command, String headerKey, String descKey) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message(headerKey, Map.of("root", root)));
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName));
        if (players.isEmpty()) {
            sendHelpLine(sender, messageService.message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (Player player : players) {
            String name = player.getName();
            sendValueEntry(sender, name,
                    messageService.message(descKey, Map.of("value", name)),
                    root + " " + command + " " + name);
        }
        sendHelpFooter(sender);
    }

    private void sendModuleHelp(CommandSender sender, String root) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message("command.help_debug_module_header", Map.of("root", root)));
        List<String> modules = plugin.debugCommand() == null
                ? List.of()
                : plugin.debugCommand().tabComplete(new String[] {"module", ""});
        if (modules.isEmpty()) {
            sendHelpLine(sender, messageService.message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String module : modules) {
            sendValueEntry(sender, module,
                    messageService.message("command.help_value_desc_debug_module", Map.of("value", module)),
                    root + " debug module " + module);
        }
        sendHelpFooter(sender);
    }

    private void sendDebugActionHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 3 && "run".equalsIgnoreCase(path.get(2))) {
            sendHelpLines(sender, root, "command.help_debug_action_run_header",
                    "command.help_debug_action_run_entry_shadow", "command.help_debug_action_run_entry_execute");
            sendHelpFooter(sender);
            return;
        }
        sendHelpLines(sender, root, "command.help_debug_action_header",
                "command.help_debug_action_entry_run", "command.help_debug_action_entry_check",
                "command.help_debug_action_entry_registry");
        sendHelpFooter(sender);
    }

    private void sendValueEntry(CommandSender sender, String value, String desc, String run) {
        sendHelpLine(sender, plugin.messageService().message("command.help_value_entry", Map.of(
                "value", value,
                "desc", desc,
                "run", run)));
    }

    private void sendHelpLines(CommandSender sender, String root, String... keys) {
        var messageService = plugin.messageService();
        Map<String, ?> replacements = Map.of("root", root);
        for (String key : keys) {
            sendHelpLine(sender, messageService.message(key, replacements));
        }
    }

    private void sendHelpLine(CommandSender sender, String line) {
        var messageService = plugin.messageService();
        messageService.sendComponent(sender, messageService.render(line));
    }

    private void sendHelpFooter(CommandSender sender) {
        sendHelpLine(sender, plugin.messageService().message("command.help_footer"));
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
