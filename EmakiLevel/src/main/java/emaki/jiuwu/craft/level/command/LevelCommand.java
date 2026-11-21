package emaki.jiuwu.craft.level.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.LevelPermissions;

import emaki.jiuwu.craft.level.api.LevelOperationResult;
import emaki.jiuwu.craft.level.api.LevelUpCause;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;
import emaki.jiuwu.craft.level.service.LevelTopService;
import emaki.jiuwu.craft.level.service.PlayerLevelService;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport.Guard;

public final class LevelCommand implements CommandExecutor, TabCompleter {

    private final EmakiLevelPlugin plugin;

    public LevelCommand(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(LevelPermissions.USE)) {
            plugin.messages().send(sender, "command.no_permission");
            return true;
        }
        if (args.length == 0) {
            return handleInfo(sender, new String[0]);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelpPage(sender, helpRoot(label), List.of(tail(args)));
                yield true;
            }
            case "info" -> handleInfo(sender, tail(args));
            case "gui", "open" -> handleGui(sender, tail(args));
            case "edit" -> handleEdit(sender, tail(args));
            case "levelup", "up" -> handleLevelUp(sender, tail(args));
            case "top" -> handleTop(sender, tail(args));
            case "topgui", "topopen" -> handleTopGui(sender, tail(args));
            case "giveexp", "addexp" -> handleExp(sender, tail(args), "add");
            case "takeexp", "removeexp" -> handleExp(sender, tail(args), "remove");
            case "setexp" -> handleExp(sender, tail(args), "set");
            case "addlevel", "givelevel" -> handleLevel(sender, tail(args), "add");
            case "takelevel", "removelevel" -> handleLevel(sender, tail(args), "remove");
            case "setlevel" -> handleLevel(sender, tail(args), "set");
            case "reset" -> handleReset(sender, tail(args));
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender, tail(args));
            default -> {
                plugin.messages().send(sender, "command.usage");
                yield true;
            }
        };
    }

    private String helpRoot(String label) {
        return "/" + (label == null || label.isBlank() ? "elv" : label);
    }

    private void sendHelpPage(CommandSender sender, String root, List<String> path) {
        if (path.isEmpty()) {
            sendHelpLines(sender, root, "command.help_header",
                    "command.help_entry_help", "command.help_entry_info", "command.help_entry_gui",
                    "command.help_entry_levelup", "command.help_entry_top", "command.help_entry_topgui",
                    "command.help_entry_edit", "command.help_entry_giveexp", "command.help_entry_takeexp",
                    "command.help_entry_setexp", "command.help_entry_addlevel", "command.help_entry_takelevel",
                    "command.help_entry_setlevel", "command.help_entry_reset", "command.help_entry_reload",
                    "command.help_entry_debug");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(0).toLowerCase(Locale.ROOT)) {
            case "debug" -> sendDebugHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of());
        }
    }

    private void sendDebugHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() < 2) {
            sendHelpLines(sender, root, "command.help_debug_header",
                    "command.help_debug_entry_status", "command.help_debug_entry_on",
                    "command.help_debug_entry_off", "command.help_debug_entry_player",
                    "command.help_debug_entry_module", "command.help_debug_entry_action",
                    "command.help_debug_entry_requirement", "command.help_debug_entry_pdc");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(1).toLowerCase(Locale.ROOT)) {
            case "player" -> sendPlayerValueHelp(sender, root, "command.help_debug_player_header",
                    "command.help_value_desc_debug_player", "debug player");
            case "module" -> sendDebugModuleHelp(sender, root);
            case "action" -> sendDebugActionHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of("debug"));
        }
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

    private void sendDebugModuleHelp(CommandSender sender, String root) {
        var messageService = plugin.messages();
        sendHelpLine(sender, messageService.message("command.help_debug_module_header", Map.of("root", root)));
        List<String> modules = new ArrayList<>(plugin.debugCommand().tabComplete(new String[]{"module", ""}));
        modules.sort(Comparator.naturalOrder());
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

    private void sendPlayerValueHelp(CommandSender sender, String root, String headerKey, String descKey, String runPrefix) {
        var messageService = plugin.messages();
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
                    root + " " + runPrefix + " " + name);
        }
        sendHelpFooter(sender);
    }

    private void sendValueEntry(CommandSender sender, String value, String desc, String run) {
        sendHelpLine(sender, plugin.messages().message("command.help_value_entry", Map.of(
                "value", value,
                "desc", desc,
                "run", run)));
    }

    private void sendHelpLines(CommandSender sender, String root, String... keys) {
        var messageService = plugin.messages();
        Map<String, ?> replacements = Map.of("root", root);
        for (String key : keys) {
            sendHelpLine(sender, messageService.message(key, replacements));
        }
    }

    private void sendHelpLine(CommandSender sender, String line) {
        plugin.messages().sendRaw(sender, line);
    }

    private void sendHelpFooter(CommandSender sender) {
        sendHelpLine(sender, plugin.messages().message("command.help_footer"));
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.player_only");
            return true;
        }
        PlayerLevelData data = plugin.dataStore().cached(player.getUniqueId());
        if (data == null) {
            plugin.messages().send(sender, "failure.player_data_unavailable");
            return true;
        }
        if (args.length > 0) {
            LevelTypeConfig type = plugin.typeRegistry().type(args[0]).orElse(null);
            if (type == null) {
                plugin.messages().send(sender, "command.type_not_found", Map.of("type", args[0]));
                return true;
            }
            sendInfoLine(sender, type, data.entry(type.id()));
            return true;
        }
        for (LevelTypeConfig type : plugin.typeRegistry().all()) {
            sendInfoLine(sender, type, data.entry(type.id()));
        }
        return true;
    }

    private boolean handleGui(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.player_only");
            return true;
        }
        String typeId = args.length > 0 ? args[0] : plugin.appConfig().primaryType();
        plugin.levelGuiService().open(player, typeId);
        return true;
    }

    private boolean handleTopGui(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.player_only");
            return true;
        }
        if (!sender.hasPermission(LevelPermissions.TOP)) {
            plugin.messages().send(sender, "command.no_permission");
            return true;
        }
        String typeId = args.length > 0 ? args[0] : plugin.appConfig().primaryType();
        plugin.levelTopGuiService().open(player, typeId);
        return true;
    }

    private boolean handleLevelUp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.player_only");
            return true;
        }
        if (!sender.hasPermission(LevelPermissions.LEVELUP)) {
            plugin.messages().send(sender, "command.no_permission");
            return true;
        }
        String typeId = args.length > 0 ? args[0] : plugin.appConfig().primaryType();
        LevelOperationResult result = plugin.levelService().levelUp(player.getUniqueId(), typeId, LevelUpCause.MANUAL);
        if (result.success()) {
            plugin.messages().send(sender, "level.levelup_success", Map.of("type_display_name", display(typeId), "new_level", String.valueOf(result.newLevel())));
        } else {
            plugin.messages().send(sender, "level.levelup_failure", Map.of("failure_reason", failure(result.reason())));
        }
        return true;
    }

    private boolean handleTop(CommandSender sender, String[] args) {
        if (!sender.hasPermission(LevelPermissions.TOP)) {
            plugin.messages().send(sender, "command.no_permission");
            return true;
        }
        String typeId = args.length > 0 ? args[0] : plugin.appConfig().primaryType();
        int index = 1;
        plugin.messages().send(sender, "command.top_header", Map.of("type", String.valueOf(typeId)));
        for (LevelTopService.TopEntry entry : plugin.topService().top(typeId, 10)) {
            plugin.messages().send(sender, "command.top_entry", Map.of(
                    "rank", String.valueOf(index++),
                    "player", String.valueOf(entry.name()),
                    "level", String.valueOf(entry.level()),
                    "total_exp", PlayerLevelService.format(entry.totalExp())));
        }
        return true;
    }

    private boolean handleExp(CommandSender sender, String[] args, String mode) {
        if (!sender.hasPermission(LevelPermissions.ADMIN)) {
            plugin.messages().send(sender, "command.no_permission");
            return true;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "command.usage_exp", Map.of("mode", mode));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.messages().send(sender, "command.player_not_found", Map.of("player", args[0]));
            return true;
        }
        Double amount = parseDouble(sender, args[2]);
        if (amount == null) {
            return true;
        }
        LevelOperationResult result = switch (mode) {
            case "add" -> plugin.levelService().addExp(target.getUniqueId(), args[1], amount, "command");
            case "remove" -> plugin.levelService().removeExp(target.getUniqueId(), args[1], amount, "command");
            default -> plugin.levelService().setExp(target.getUniqueId(), args[1], amount, "command");
        };
        sendOperationResult(sender, result);
        return true;
    }

    private boolean handleLevel(CommandSender sender, String[] args, String mode) {
        if (!sender.hasPermission(LevelPermissions.ADMIN)) {
            plugin.messages().send(sender, "command.no_permission");
            return true;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "command.usage_level", Map.of("mode", mode));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.messages().send(sender, "command.player_not_found", Map.of("player", args[0]));
            return true;
        }
        Integer amount = parseInt(sender, args[2]);
        if (amount == null) {
            return true;
        }
        LevelOperationResult result = switch (mode) {
            case "add" -> plugin.levelService().addLevel(target.getUniqueId(), args[1], amount, "command");
            case "remove" -> plugin.levelService().removeLevel(target.getUniqueId(), args[1], amount, "command");
            default -> plugin.levelService().setLevel(target.getUniqueId(), args[1], amount, "command");
        };
        sendOperationResult(sender, result);
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission(LevelPermissions.ADMIN)) {
            plugin.messages().send(sender, "command.no_permission");
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "command.usage_reset");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.messages().send(sender, "command.player_not_found", Map.of("player", args[0]));
            return true;
        }
        LevelOperationResult result = plugin.levelService().reset(target.getUniqueId(), args[1]);
        sendOperationResult(sender, result);
        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        EditorCommandSupport.Guard editGuard = EditorCommandSupport.check(
                sender, () -> plugin.editorService() != null && plugin.editorModule() != null, LevelPermissions.EDIT);
        if (editGuard == Guard.DENIED) {
            plugin.messages().send(sender, "command.no_permission");
            return true;
        }
        if (editGuard == Guard.NON_PLAYER) {
            plugin.messages().send(sender, "command.player_only");
            return true;
        }
        if (editGuard == Guard.UNAVAILABLE) {
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            plugin.editorService().openBrowser(player);
            return true;
        }
        if (plugin.editorModule().openById(player, args[0]) == null) {
            plugin.messages().send(sender, "editor.open_failed", Map.of("id", args[0]));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(LevelPermissions.RELOAD) && !sender.hasPermission(LevelPermissions.ADMIN)) {
            plugin.messages().send(sender, "command.no_permission");
            return true;
        }
        plugin.dataStore().saveAll();
        long startTime = System.currentTimeMillis();
        plugin.reloadPluginState();
        long elapsedMs = System.currentTimeMillis() - startTime;
        plugin.messages().send(sender, "command.reload_success");
        plugin.messages().send(sender, "command.reload_time", Map.of("elapsed", String.valueOf(elapsedMs)));
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(LevelPermissions.DEBUG) && !sender.hasPermission(LevelPermissions.ADMIN)) {
            plugin.messages().send(sender, "command.no_permission");
            return true;
        }
        if (args.length >= 3 && "requirement".equalsIgnoreCase(args[0])) {
            LevelTypeConfig type = plugin.typeRegistry().type(args[1]).orElse(null);
            Integer targetLevel = parseInt(sender, args[2]);
            if (type == null || targetLevel == null) {
                return true;
            }
            double required = plugin.requirementService().requiredExp(type, null, targetLevel);
            String source = plugin.requirementService().debugSource(type, targetLevel);
            plugin.messages().send(sender, "command.debug_requirement", Map.of(
                    "type", String.valueOf(type.id()),
                    "level", String.valueOf(targetLevel),
                    "required", PlayerLevelService.format(required),
                    "source", String.valueOf(source)));
            return true;
        }
        if (args.length >= 2 && "pdc".equalsIgnoreCase(args[0])) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "command.player_not_found", Map.of("player", args[1]));
                return true;
            }
            plugin.levelService().syncAllOnline();
            plugin.messages().send(sender, "command.debug_pdc_synced", Map.of("player", String.valueOf(target.getName())));
            return true;
        }
        return plugin.debugCommand().handle(sender, args, plugin.debugMessageService());
    }

    private void sendInfoLine(CommandSender sender, LevelTypeConfig type, PlayerLevelEntry entry) {
        if (entry == null) {
            return;
        }
        Map<String, Object> placeholders = plugin.levelService().displayPlaceholders(type, entry);
        plugin.messages().send(sender, "level.info_line", placeholders);
    }

    private void sendOperationResult(CommandSender sender, LevelOperationResult result) {
        if (result.success()) {
            plugin.messages().send(sender, "command.operation_success", Map.of(
                    "type", String.valueOf(result.typeId()),
                    "old_level", String.valueOf(result.oldLevel()),
                    "new_level", String.valueOf(result.newLevel()),
                    "old_exp", PlayerLevelService.format(result.oldExp()),
                    "new_exp", PlayerLevelService.format(result.newExp())));
        } else {
            plugin.messages().send(sender, "command.operation_failed", Map.of("reason", String.valueOf(failure(result.reason()))));
        }
    }

    private String display(String typeId) {
        return plugin.typeRegistry().type(typeId).map(LevelTypeConfig::displayName).orElse(typeId);
    }

    private String failure(String reason) {
        return plugin.messages().message("failure." + reason);
    }

    private Double parseDouble(CommandSender sender, String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            plugin.messages().send(sender, "command.invalid_number", Map.of("value", value));
            return null;
        }
    }

    private Integer parseInt(CommandSender sender, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            plugin.messages().send(sender, "command.invalid_number", Map.of("value", value));
            return null;
        }
    }

    private static String[] tail(String[] args) {
        String[] result = new String[Math.max(0, args.length - 1)];
        if (result.length > 0) {
            System.arraycopy(args, 1, result, 0, result.length);
        }
        return result;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            return CommandTabHelper.completeSubcommands(
                    List.of("help", "info", "gui", "open", "edit", "levelup", "up", "top", "topgui", "topopen",
                            "giveexp", "addexp", "takeexp", "removeexp", "setexp", "addlevel", "givelevel",
                            "takelevel", "removelevel", "setlevel", "reset", "reload", "debug"),
                    args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        sub = switch (sub) {
            case "open" -> "gui";
            case "up" -> "levelup";
            case "topopen" -> "topgui";
            case "addexp" -> "giveexp";
            case "removeexp" -> "takeexp";
            case "givelevel" -> "addlevel";
            case "removelevel" -> "takelevel";
            default -> sub;
        };
        if ("edit".equals(sub) && args.length == 2) {
            if (plugin.editorModule() != null) {
                for (String id : plugin.editorModule().completeIds()) {
                    addIfStarts(result, id, args[1]);
                }
            }
            return result;
        }
        if (List.of("giveexp", "takeexp", "setexp", "addlevel", "takelevel", "setlevel", "reset").contains(sub)) {
            if (args.length == 2) {
                result.addAll(CommandTabHelper.completeOnlinePlayers(args[1]));
            } else if (args.length == 3) {
                completeTypes(result, args[2]);
            } else if (args.length == 4 && !"reset".equals(sub)) {
                result.addAll(CommandTabHelper.completeIntRange(args[3], 1, 100));
            }
            return result;
        }
        if (("info".equals(sub) || "gui".equals(sub) || "levelup".equals(sub) || "top".equals(sub) || "topgui".equals(sub)) && args.length == 2) {
            completeTypes(result, args[1]);
        }
        if ("debug".equals(sub)) {
            if (args.length == 2) {
                addIfStarts(result, "requirement", args[1]);
                addIfStarts(result, "pdc", args[1]);
                for (String candidate : plugin.debugCommand().tabComplete(new String[]{args[1]})) {
                    addIfStarts(result, candidate, args[1]);
                }
            } else if (args.length == 3 && "requirement".equalsIgnoreCase(args[1])) {
                completeTypes(result, args[2]);
            } else if (args.length == 4 && "requirement".equalsIgnoreCase(args[1])) {
                result.addAll(CommandTabHelper.completeIntRange(args[3], 1, 100));
            } else if (args.length == 3 && "pdc".equalsIgnoreCase(args[1])) {
                result.addAll(CommandTabHelper.completeOnlinePlayers(args[2]));
            } else if (args.length == 3) {
                result.addAll(plugin.debugCommand().tabComplete(new String[]{args[1], args[2]}));
            }
        }
        return result;
    }

    private void completeTypes(List<String> result, String prefix) {
        for (LevelTypeConfig type : plugin.typeRegistry().all()) {
            addIfStarts(result, type.id(), prefix);
        }
    }

    private static void addIfStarts(List<String> result, String candidate, String prefix) {
        if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
            result.add(candidate);
        }
    }
}
