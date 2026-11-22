package emaki.jiuwu.craft.cooking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.cooking.model.NutritionOperationResult;
import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.command.CommandKeys;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport.Guard;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import emaki.jiuwu.craft.cooking.log.CookingLogTag;
import java.util.logging.Level;

final class CookingCommandRouter implements TabExecutor {

    private static final String PERMISSION_USE = CookingPermissions.USE;
    private static final String PERMISSION_RELOAD = CookingPermissions.RELOAD;
    private static final String PERMISSION_INSPECT = CookingPermissions.INSPECT;
    private static final String PERMISSION_ADMIN = CookingPermissions.ADMIN;
    private static final String PERMISSION_EDIT = CookingPermissions.EDIT;
    private static final String PERMISSION_DEBUG = "emakicooking.debug";

    private final EmakiCookingPlugin plugin;

    CookingCommandRouter(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelpPage(sender, helpRoot(label), Arrays.asList(args).subList(1, args.length));
                yield true;
            }
            case "edit" -> handleEdit(sender, args);
            case "reload" -> handleReload(sender);
            case "inspect" -> handleInspect(sender, args);
            case "station" -> handleStation(sender, args);
            case "nutrition" -> handleNutrition(sender, args);
            case "debug" -> handleDebug(sender, args);
            default -> {
                plugin.messageService().send(sender, "general.unknown_command");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("help");
            if (sender.hasPermission(PERMISSION_EDIT)) {
                subcommands.add("edit");
            }
            if (sender.hasPermission(PERMISSION_RELOAD) || sender.hasPermission(PERMISSION_ADMIN)) {
                subcommands.add("reload");
            }
            if (sender.hasPermission(PERMISSION_INSPECT) || sender.hasPermission(PERMISSION_ADMIN)
                    || sender.hasPermission(PERMISSION_USE)) {
                subcommands.add("inspect");
            }
            if (sender.hasPermission(PERMISSION_ADMIN)) {
                subcommands.add("station");
            }
            subcommands.add("nutrition");
            if (sender.hasPermission(PERMISSION_DEBUG) || sender.hasPermission(PERMISSION_ADMIN)) {
                subcommands.add("debug");
            }
            return CommandTabHelper.completeSubcommands(subcommands, args[0]);
        }
        if (args.length >= 2 && "debug".equalsIgnoreCase(args[0])
                && (sender.hasPermission(PERMISSION_DEBUG) || sender.hasPermission(PERMISSION_ADMIN))) {
            return plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 2 && "edit".equalsIgnoreCase(args[0]) && sender.hasPermission(PERMISSION_EDIT)) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (plugin.editorModule() != null) {
                for (String id : plugin.editorModule().knownIds()) {
                    if (id.startsWith(prefix)) {
                        result.add(id);
                    }
                }
            }
            return result;
        }
        if ("nutrition".equalsIgnoreCase(args[0])) {
            return nutritionTabComplete(sender, args);
        }
        if (args.length == 2) {
            if ("inspect".equalsIgnoreCase(args[0])
                    && (sender.hasPermission(PERMISSION_INSPECT) || sender.hasPermission(PERMISSION_ADMIN)
                            || sender.hasPermission(PERMISSION_USE))) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (String option : List.of("hand", "block")) {
                    if (option.startsWith(prefix)) {
                        result.add(option);
                    }
                }
            }
            if ("station".equalsIgnoreCase(args[0]) && sender.hasPermission(PERMISSION_ADMIN)
                    && "reindex".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                result.add("reindex");
            }
            return result;
        }
        return result;
    }

    private List<String> nutritionTabComplete(CommandSender sender, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 2) {
            for (String sub : List.of("get", "set", "add", "remove")) {
                if (sub.startsWith(args[1].toLowerCase(Locale.ROOT)) && canNutrition(sender, sub)) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (!canNutrition(sender, args[1])) {
            return result;
        }
        if (args.length == 3) {
            result.addAll(CommandTabHelper.completeOnlinePlayers(args[2]));
            if ("get".equalsIgnoreCase(args[1]) && plugin.nutritionTypeRegistry() != null) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                plugin.nutritionTypeRegistry().all().forEach(type -> {
                    if (type.id().startsWith(prefix)) {
                        result.add(type.id());
                    }
                });
            }
            return result;
        }
        if (args.length == 4 && plugin.nutritionTypeRegistry() != null
                && Bukkit.getPlayerExact(args[2]) != null) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            plugin.nutritionTypeRegistry().all().forEach(type -> {
                if (type.id().startsWith(prefix)) {
                    result.add(type.id());
                }
            });
            return result;
        }
        return result;
    }

    private boolean canNutrition(CommandSender sender, String action) {
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "get" -> sender.hasPermission(CookingPermissions.NUTRITION_USE)
                    || sender.hasPermission(CookingPermissions.NUTRITION_ADMIN)
                    || sender.hasPermission(PERMISSION_ADMIN);
            case "set", "add", "remove" -> sender.hasPermission(CookingPermissions.NUTRITION_ADMIN)
                    || sender.hasPermission(PERMISSION_ADMIN);
            default -> true;
        };
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        EditorCommandSupport.Guard editGuard = EditorCommandSupport.check(
                sender, () -> plugin.editorService() != null && plugin.editorModule() != null, PERMISSION_EDIT);
        if (editGuard == Guard.DENIED) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (editGuard == Guard.NON_PLAYER) {
            plugin.messageService().send(sender, "general.player_only");
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
            plugin.messageService().send(sender, "editor.open_failed",
                    Map.of("id", Texts.normalizeId(args[1])));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.messageService().send(sender, "general.reloading");
        long startTime = System.currentTimeMillis();
        plugin.reloadPluginStateAsync().whenComplete((_, failure) -> runForSender(sender, () -> {
            if (failure != null) {
                LogTags.send(plugin, CookingLogTag.COOKING, Level.WARNING, "cooking.config_reload_failed", Map.of("error", AsyncFailures.describe(failure)));
                plugin.messageService().send(sender, "general.reload_failed", Map.of(
                        "reason", AsyncFailures.describe(failure)
                ));
                return;
            }
            long elapsedMs = System.currentTimeMillis() - startTime;
            plugin.messageService().send(sender, "general.reload_success");
            plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of(
                    "recipes", totalRecipeCount(),
                    "resources", 1
            )));
            plugin.messageService().send(sender, "command.reload_elapsed", Map.of("elapsed_ms", elapsedMs));
        }));
        return true;
    }

    private void runForSender(CommandSender sender, Runnable task) {
        EmakiScheduling scheduler = plugin.taskScheduler();
        if (scheduler == null) {
            task.run();
            return;
        }
        if (sender instanceof Player player) {
            scheduler.runForEntity(plugin, player, task, null);
            return;
        }
        scheduler.runGlobal(plugin, task);
    }

    private boolean handleInspect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_INSPECT) && !sender.hasPermission(PERMISSION_ADMIN)
                && !sender.hasPermission(PERMISSION_USE)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "hand" -> plugin.inspectService().inspectHand(sender, player);
            case "block" -> plugin.inspectService().inspectBlock(sender, player);
            default -> {
                plugin.messageService().send(sender, "general.invalid_args");
                yield true;
            }
        };
    }

    private boolean handleStation(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (args.length < 2 || !"reindex".equalsIgnoreCase(args[1])) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        if (plugin.stationStateStore() == null) {
            plugin.messageService().send(sender, "command.station.store_not_ready");
            return true;
        }
        plugin.messageService().send(sender, "command.station.reindex_started");
        plugin.stationStateStore().reindexAsync().whenComplete((report, failure) -> runForSender(sender, () -> {
            if (failure != null) {
                LogTags.send(plugin, LogTag.PERSISTENCE, Level.WARNING, "persistence.station_index_rebuild_failed", Map.of("error", AsyncFailures.describe(failure)));
                plugin.messageService().send(sender, "command.station.reindex_failed", Map.of(
                        "reason", AsyncFailures.describe(failure)
                ));
                return;
            }
            if (report == null) {
                plugin.messageService().send(sender, "command.station.reindex_failed", Map.of(
                        "reason", "重建结果缺失"
                ));
                return;
            }
            if (!report.success()) {
                LogTags.send(plugin, LogTag.PERSISTENCE, Level.WARNING, "persistence.station_index_rebuild_partial_failed", Map.of("failure_summary", report.failureSummary()));
                plugin.messageService().send(sender, "command.station.reindex_partial", Map.of(
                        "legacy_yaml", report.legacyYamlStates(),
                        "loaded_pdc", report.loadedPdcStates(),
                        "total_indexed", report.totalIndexedStates(),
                        "failures", report.failureSummary()
                ));
                return;
            }
            plugin.messageService().sendRaw(sender, plugin.messageService().message("command.station.reindex_done", Map.of(
                    "legacy_yaml", report.legacyYamlStates(),
                    "loaded_pdc", report.loadedPdcStates(),
                    "total_indexed", report.totalIndexedStates())));
        }));
        return true;
    }

    private int totalRecipeCount() {
        return plugin.choppingBoardRecipeLoader().all().size()
                + plugin.wokRecipeLoader().all().size()
                + plugin.grinderRecipeLoader().all().size()
                + plugin.steamerRecipeLoader().all().size();
    }

    private void sendHelp(CommandSender sender, String label) {
        sendHelpPage(sender, helpRoot(label), List.of());
    }

    private String helpRoot(String label) {
        return "/" + (label == null || label.isBlank() ? "ecooking" : label);
    }

    private void sendHelpPage(CommandSender sender, String root, List<String> path) {
        if (path.isEmpty()) {
            List<String> entries = new ArrayList<>();
            entries.add("command.help_header");
            if (sender.hasPermission(PERMISSION_EDIT)) {
                entries.add("command.help_entry_edit");
            }
            if (sender.hasPermission(PERMISSION_RELOAD) || sender.hasPermission(PERMISSION_ADMIN)) {
                entries.add("command.help_entry_reload");
            }
            if (sender.hasPermission(PERMISSION_INSPECT) || sender.hasPermission(PERMISSION_ADMIN)
                    || sender.hasPermission(PERMISSION_USE)) {
                entries.add("command.help_entry_inspect");
            }
            if (sender.hasPermission(PERMISSION_ADMIN)) {
                entries.add("command.help_entry_station");
            }
            entries.add("command.help_entry_nutrition");
            if (sender.hasPermission(PERMISSION_DEBUG) || sender.hasPermission(PERMISSION_ADMIN)) {
                entries.add("command.help_entry_debug");
            }
            entries.add("command.help_entry_help");
            sendHelpLines(sender, root, entries.toArray(String[]::new));
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(0).toLowerCase(Locale.ROOT)) {
            case "edit" -> sendValuePage(sender, root, "command.help_edit_id_header",
                    "command.help_value_desc_edit_id", "edit", editorIds(),
                    sender.hasPermission(PERMISSION_EDIT));
            case "inspect" -> {
                List<String> entries = new ArrayList<>();
                entries.add("command.help_inspect_header");
                if (sender.hasPermission(PERMISSION_INSPECT) || sender.hasPermission(PERMISSION_ADMIN)
                        || sender.hasPermission(PERMISSION_USE)) {
                    entries.addAll(List.of("command.help_inspect_entry_hand", "command.help_inspect_entry_block"));
                }
                sendHelpLines(sender, root, entries.toArray(String[]::new));
                sendHelpFooter(sender);
            }
            case "station" -> {
                List<String> entries = new ArrayList<>();
                entries.add("command.help_station_header");
                if (sender.hasPermission(PERMISSION_ADMIN)) {
                    entries.add("command.help_station_entry_reindex");
                }
                sendHelpLines(sender, root, entries.toArray(String[]::new));
                sendHelpFooter(sender);
            }
            case "nutrition" -> sendNutritionHelp(sender, root, path);
            case "debug" -> sendDebugHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of());
        }
    }

    private void sendNutritionHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 2 && "get".equalsIgnoreCase(path.get(1))) {
            sendNutritionGetHelp(sender, root, path);
            return;
        }
        List<String> entries = new ArrayList<>();
        entries.add("command.help_nutrition_header");
        if (canNutrition(sender, "get")) {
            entries.add("command.help_nutrition_entry_get");
        }
        if (canNutrition(sender, "set")) {
            entries.addAll(List.of("command.help_nutrition_entry_set", "command.help_nutrition_entry_add",
                    "command.help_nutrition_entry_remove"));
        }
        sendHelpLines(sender, root, entries.toArray(String[]::new));
        sendHelpFooter(sender);
    }

    private void sendNutritionGetHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 3) {
            String tail = path.get(2).toLowerCase(Locale.ROOT);
            if ("player".equals(tail)) {
                sendValuePage(sender, root, "command.help_nutrition_get_player_header",
                        "command.help_value_desc_nutrition_get_player", "nutrition get", onlinePlayerNames(),
                        canNutrition(sender, "get"));
                return;
            }
            if ("type".equals(tail)) {
                sendValuePage(sender, root, "command.help_nutrition_get_type_header",
                        "command.help_value_desc_nutrition_get_type", "nutrition get", nutritionTypeIds(),
                        canNutrition(sender, "get"));
                return;
            }
        }
        List<String> entries = new ArrayList<>();
        entries.add("command.help_nutrition_get_header");
        if (canNutrition(sender, "get")) {
            entries.addAll(List.of("command.help_nutrition_get_entry_player",
                    "command.help_nutrition_get_entry_type"));
        }
        sendHelpLines(sender, root, entries.toArray(String[]::new));
        sendHelpFooter(sender);
    }

    private void sendDebugHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() < 2) {
            List<String> entries = new ArrayList<>();
            entries.add("command.help_debug_header");
            if (sender.hasPermission(PERMISSION_DEBUG) || sender.hasPermission(PERMISSION_ADMIN)) {
                entries.addAll(List.of("command.help_debug_entry_status", "command.help_debug_entry_on",
                        "command.help_debug_entry_off", "command.help_debug_entry_player",
                        "command.help_debug_entry_module", "command.help_debug_entry_action"));
            }
            sendHelpLines(sender, root, entries.toArray(String[]::new));
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(1).toLowerCase(Locale.ROOT)) {
            case "player" -> sendValuePage(sender, root, "command.help_debug_player_header",
                    "command.help_value_desc_debug_player", "debug player", onlinePlayerNames(),
                    sender.hasPermission(PERMISSION_DEBUG) || sender.hasPermission(PERMISSION_ADMIN));
            case "action" -> sendDebugActionHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of("debug"));
        }
    }

    private void sendDebugActionHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 3 && "run".equalsIgnoreCase(path.get(2))) {
            List<String> entries = new ArrayList<>();
            entries.add("command.help_debug_action_run_header");
            if (sender.hasPermission(PERMISSION_DEBUG) || sender.hasPermission(PERMISSION_ADMIN)) {
                entries.addAll(List.of("command.help_debug_action_run_entry_shadow",
                        "command.help_debug_action_run_entry_execute"));
            }
            sendHelpLines(sender, root, entries.toArray(String[]::new));
            sendHelpFooter(sender);
            return;
        }
        List<String> entries = new ArrayList<>();
        entries.add("command.help_debug_action_header");
        if (sender.hasPermission(PERMISSION_DEBUG) || sender.hasPermission(PERMISSION_ADMIN)) {
            entries.addAll(List.of("command.help_debug_action_entry_run", "command.help_debug_action_entry_check",
                    "command.help_debug_action_entry_registry"));
        }
        sendHelpLines(sender, root, entries.toArray(String[]::new));
        sendHelpFooter(sender);
    }

    private void sendValuePage(CommandSender sender, String root, String headerKey, String descKey,
            String command, List<String> values, boolean permitted) {
        sendHelpLine(sender, plugin.messageService().message(headerKey, Map.of("root", root)));
        if (!permitted) {
            sendHelpFooter(sender);
            return;
        }
        if (values.isEmpty()) {
            sendHelpLine(sender, plugin.messageService().message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String value : values) {
            sendValueEntry(sender, value,
                    plugin.messageService().message(descKey, Map.of("value", value)),
                    root + " " + command + " " + value);
        }
        sendHelpFooter(sender);
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private List<String> editorIds() {
        List<String> ids = new ArrayList<>();
        if (plugin.editorModule() != null) {
            ids.addAll(plugin.editorModule().knownIds());
        }
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    private List<String> nutritionTypeIds() {
        List<String> ids = new ArrayList<>();
        if (plugin.nutritionTypeRegistry() != null) {
            plugin.nutritionTypeRegistry().all().forEach(type -> ids.add(type.id()));
        }
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    private void sendValueEntry(CommandSender sender, String value, String desc, String run) {
        sendHelpLine(sender, plugin.messageService().message("command.help_value_entry", Map.of(
                "value", value,
                "desc", desc,
                "run", run)));
    }

    private void sendHelpLines(CommandSender sender, String root, String... keys) {
        Map<String, ?> replacements = Map.of("root", root);
        for (String key : keys) {
            sendHelpLine(sender, plugin.messageService().message(key, replacements));
        }
    }

    private void sendHelpLine(CommandSender sender, String line) {
        plugin.messageService().sendComponent(sender, plugin.messageService().render(line));
    }

    private void sendHelpFooter(CommandSender sender) {
        sendHelpLine(sender, plugin.messageService().message("command.help_footer"));
    }

    private boolean handleNutrition(CommandSender sender, String[] args) {
        if (plugin.nutritionService() == null) {
            plugin.messageService().send(sender, "nutrition.disabled");
            return true;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        return switch (action) {
            case "get" -> handleNutritionGet(sender, args);
            case "set", "add", "remove" -> handleNutritionModify(sender, args, action);
            default -> {
                plugin.messageService().send(sender, "nutrition.usage");
                yield true;
            }
        };
    }

    private boolean handleNutritionGet(CommandSender sender, String[] args) {
        if (!sender.hasPermission(CookingPermissions.NUTRITION_USE)
                && !sender.hasPermission(CookingPermissions.NUTRITION_ADMIN)
                && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }

        Player target;
        String typeArg;
        if (args.length >= 3 && Bukkit.getPlayerExact(args[2]) != null) {
            target = Bukkit.getPlayerExact(args[2]);
            typeArg = args.length >= 4 ? args[3] : null;
        } else if (sender instanceof Player self) {
            target = self;
            typeArg = args.length >= 3 ? args[2] : null;
        } else {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (typeArg != null) {
            if (!plugin.nutritionTypeRegistry().contains(typeArg)) {
                plugin.messageService().send(sender, "nutrition.unknown_type", Map.of("type", typeArg));
                return true;
            }
            double value = plugin.nutritionService().value(target.getUniqueId(), typeArg);
            plugin.messageService().send(sender, "nutrition.value", Map.of(
                    "player", target.getName(),
                    "type", Texts.normalizeId(typeArg),
                    "value", formatValue(value)
            ));
            return true;
        }
        plugin.messageService().send(sender, "nutrition.list_header", Map.of("player", target.getName()));
        Player finalTarget = target;
        plugin.nutritionTypeRegistry().all().forEach(type -> plugin.messageService().sendRaw(
                sender,
                plugin.messageService().message("nutrition.list_line", Map.of(
                        "type", type.id(),
                        "value", formatValue(plugin.nutritionService().value(finalTarget.getUniqueId(), type.id())),
                        "max", formatValue(type.max())
                ))
        ));
        return true;
    }

    private boolean handleNutritionModify(CommandSender sender, String[] args, String action) {
        if (!sender.hasPermission(CookingPermissions.NUTRITION_ADMIN) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }

        if (args.length < 5) {
            plugin.messageService().send(sender, "nutrition.usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.messageService().send(sender, "nutrition.player_not_found", Map.of("player", args[2]));
            return true;
        }
        String typeArg = args[3];
        if (!plugin.nutritionTypeRegistry().contains(typeArg)) {
            plugin.messageService().send(sender, "nutrition.unknown_type", Map.of("type", typeArg));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[4]);
        } catch (NumberFormatException exception) {
            plugin.messageService().send(sender, "nutrition.invalid_amount", Map.of("amount", args[4]));
            return true;
        }
        NutritionOperationResult result = switch (action) {
            case "set" -> plugin.nutritionService().set(target.getUniqueId(), typeArg, amount);
            case "add" -> plugin.nutritionService().add(target.getUniqueId(), typeArg, amount);
            case "remove" -> plugin.nutritionService().remove(target.getUniqueId(), typeArg, amount);
            default -> null;
        };
        if (result == null || !result.success()) {
            plugin.messageService().send(sender, "nutrition.modify_failed", Map.of(
                    "reason", result == null ? "unknown" : result.reason()
            ));
            return true;
        }
        plugin.messageService().send(sender, "nutrition.modified", Map.of(
                "player", target.getName(),
                "type", result.typeId(),
                "old", formatValue(result.oldValue()),
                "new", formatValue(result.newValue())
        ));
        return true;
    }

    private String formatValue(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DEBUG) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }
}
