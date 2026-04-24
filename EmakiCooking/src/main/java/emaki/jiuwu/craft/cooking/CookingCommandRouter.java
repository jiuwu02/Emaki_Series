package emaki.jiuwu.craft.cooking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.cooking.service.LegacyImportService;

final class CookingCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakicooking";
    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_INSPECT = PERMISSION_ROOT + ".inspect";
    private static final String PERMISSION_CONVERT = PERMISSION_ROOT + ".convert";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";

    private final EmakiCookingPlugin plugin;

    CookingCommandRouter(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            case "reload" -> handleReload(sender);
            case "inspect" -> handleInspect(sender, args);
            case "convert" -> handleConvert(sender, args);
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
            for (String sub : List.of("help", "reload", "inspect", "convert")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2) {
            if ("inspect".equalsIgnoreCase(args[0]) && "hand".startsWith(args[1].toLowerCase())) {
                result.add("hand");
            }
            if ("convert".equalsIgnoreCase(args[0]) && "old".startsWith(args[1].toLowerCase())) {
                result.add("old");
            }
            return result;
        }
        if (args.length == 3 && "convert".equalsIgnoreCase(args[0]) && "old".equalsIgnoreCase(args[1])) {
            for (String mode : List.of("dryrun", "apply")) {
                if (mode.startsWith(args[2].toLowerCase())) {
                    result.add(mode);
                }
            }
        }
        return result;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.reloadPluginState();
        plugin.messageService().send(sender, "general.reload_success");
        plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of(
                "recipes", totalRecipeCount(),
                "resources", 1
        )));
        return true;
    }

    private boolean handleInspect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_INSPECT) && !sender.hasPermission(PERMISSION_ADMIN)
                && !sender.hasPermission(PERMISSION_USE)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2 || !"hand".equalsIgnoreCase(args[1])) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        return plugin.inspectService().inspectHand(sender, player);
    }

    private boolean handleConvert(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_CONVERT) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 3 || !"old".equalsIgnoreCase(args[1])) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        LegacyImportService.LegacyImportResult result = switch (args[2].toLowerCase()) {
            case "dryrun" -> plugin.legacyImportService().dryRun();
            case "apply" -> plugin.legacyImportService().apply();
            default -> null;
        };
        if (result == null) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        if (result.applied()) {
            plugin.reloadPluginState();
            plugin.messageService().send(sender, "command.convert.apply_success", Map.of(
                    "recipes", result.convertedRecipeCount(),
                    "stations", result.convertedStationCount()
            ));
        } else {
            plugin.messageService().send(sender, "command.convert.dryrun_success", Map.of(
                    "recipes", result.convertedRecipeCount(),
                    "stations", result.convertedStationCount()
            ));
        }
        plugin.messageService().send(sender, "command.convert.report", Map.of("path", result.reportPath()));
        if (result.backupPath() != null) {
            plugin.messageService().send(sender, "command.convert.backup", Map.of("path", result.backupPath()));
        }
        plugin.messageService().send(sender, "command.convert.issues", Map.of(
                "skipped", result.skippedCount(),
                "conflicts", result.conflictCount(),
                "unknown_sources", result.unknownSourceCount(),
                "action_context_issues", result.actionContextIssueCount()
        ));
        return true;
    }

    private int totalRecipeCount() {
        return plugin.choppingBoardRecipeLoader().all().size()
                + plugin.wokRecipeLoader().all().size()
                + plugin.grinderRecipeLoader().all().size()
                + plugin.steamerRecipeLoader().all().size();
    }

    private void sendHelp(CommandSender sender) {
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.header"));
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("help", "显示帮助信息");
        lines.put("reload", "重载配置、语言与配方目录");
        lines.put("inspect hand", "检查主手物品的 CoreLib 来源标识");
        lines.put("convert old dryrun", "解析旧版配置并生成导入报告，不写入新资源");
        lines.put("convert old apply", "备份当前新资源后导入旧版配置与工位数据");
        lines.forEach((name, description) -> plugin.messageService().sendRaw(
                sender,
                plugin.messageService().message("command.help.line", Map.of("cmd", name, "desc", description))
        ));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.footer"));
    }
}
