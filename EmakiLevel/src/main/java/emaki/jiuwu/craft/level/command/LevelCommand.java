package emaki.jiuwu.craft.level.command;

import java.util.ArrayList;
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
import emaki.jiuwu.craft.level.legacy.LevelLegacyEntry;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.LevelPermissions;

import emaki.jiuwu.craft.level.api.LevelOperationResult;
import emaki.jiuwu.craft.level.api.LevelUpCause;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;
import emaki.jiuwu.craft.level.service.LevelTopService;
import emaki.jiuwu.craft.level.service.PlayerLevelService;

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
            case "info" -> handleInfo(sender, tail(args));
            case "gui", "open" -> handleGui(sender, tail(args));
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
            case "convert-legacy" -> LevelLegacyEntry.handle(plugin, sender, tail(args), LevelPermissions.ADMIN);
            case "debug" -> handleDebug(sender, tail(args));
            default -> {
                plugin.messages().send(sender, "command.usage");
                yield true;
            }
        };
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
        plugin.messages().sendRaw(sender, "<gold>===== EmakiLevel Top: " + typeId + " =====</gold>");
        for (LevelTopService.TopEntry entry : plugin.topService().top(typeId, 10)) {
            plugin.messages().sendRaw(sender, "<yellow>#" + index++ + "</yellow> <white>" + entry.name() + "</white> <gray>Lv." + entry.level() + " / " + PlayerLevelService.format(entry.totalExp()) + " total</gray>");
        }
        return true;
    }

    private boolean handleExp(CommandSender sender, String[] args, String mode) {
        if (!sender.hasPermission(LevelPermissions.ADMIN)) {
            plugin.messages().send(sender, "command.no_permission");
            return true;
        }
        if (args.length < 3) {
            plugin.messages().sendRaw(sender, "<red>/elv " + mode + "exp <player> <type> <amount></red>");
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
            plugin.messages().sendRaw(sender, "<red>/elv " + mode + "level <player> <type> <amount></red>");
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
            plugin.messages().sendRaw(sender, "<red>/elv reset <player> <type></red>");
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
        plugin.messages().sendRaw(sender, "<gray>重载耗时: <white>" + elapsedMs + "ms</white></gray>");
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
            plugin.messages().sendRaw(sender, "<gray>Requirement " + type.id() + " -> " + targetLevel + ": <yellow>" + PlayerLevelService.format(required) + "</yellow> <dark_gray>(" + source + ")</dark_gray></gray>");
            return true;
        }
        if (args.length >= 2 && "pdc".equalsIgnoreCase(args[0])) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "command.player_not_found", Map.of("player", args[1]));
                return true;
            }
            plugin.levelService().syncAllOnline();
            plugin.messages().sendRaw(sender, "<green>PDC synced for " + target.getName() + ".</green>");
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
            plugin.messages().sendRaw(sender, "<green>操作成功: " + result.typeId() + " Lv." + result.oldLevel() + " → Lv." + result.newLevel() + ", exp " + PlayerLevelService.format(result.oldExp()) + " → " + PlayerLevelService.format(result.newExp()) + "</green>");
        } else {
            plugin.messages().sendRaw(sender, "<red>操作失败: " + failure(result.reason()) + "</red>");
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
            for (String candidate : List.of("info", "gui", "levelup", "top", "topgui", "giveexp", "takeexp", "setexp", "addlevel", "takelevel", "setlevel", "reset", "reload", "convert-legacy", "debug")) {
                addIfStarts(result, candidate, args[0]);
            }
            return result;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (List.of("giveexp", "takeexp", "setexp", "addlevel", "takelevel", "setlevel", "reset").contains(sub)) {
            if (args.length == 2) {
                result.addAll(CommandTabHelper.completeOnlinePlayers(args[1]));
            } else if (args.length == 3) {
                completeTypes(result, args[2]);
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
