package emaki.jiuwu.craft.cooking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

final class CookingCommandRouter implements TabExecutor {

    private static final String PERMISSION_USE = CookingPermissions.USE;
    private static final String PERMISSION_RELOAD = CookingPermissions.RELOAD;
    private static final String PERMISSION_INSPECT = CookingPermissions.INSPECT;
    private static final String PERMISSION_ADMIN = CookingPermissions.ADMIN;
    private static final String PERMISSION_DEBUG = "emakicooking.debug";

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
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
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
            for (String sub : List.of("help", "reload", "inspect", "station", "nutrition", "debug")) {
                if (sub.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length >= 2 && "debug".equalsIgnoreCase(args[0])) {
            return plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        if ("nutrition".equalsIgnoreCase(args[0])) {
            return nutritionTabComplete(args);
        }
        if (args.length == 2) {
            if ("inspect".equalsIgnoreCase(args[0])) {
                String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
                for (String option : List.of("hand", "block")) {
                    if (option.startsWith(prefix)) {
                        result.add(option);
                    }
                }
            }
            if ("station".equalsIgnoreCase(args[0]) && "reindex".startsWith(args[1].toLowerCase(java.util.Locale.ROOT))) {
                result.add("reindex");
            }
            return result;
        }
        return result;
    }

    private List<String> nutritionTabComplete(String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 2) {
            for (String sub : List.of("get", "set", "add", "remove")) {
                if (sub.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 3) {
            String prefix = args[2].toLowerCase(java.util.Locale.ROOT);
            for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                    result.add(online.getName());
                }
            }
            return result;
        }
        if (args.length == 4 && plugin.nutritionTypeRegistry() != null) {
            String prefix = args[3].toLowerCase(java.util.Locale.ROOT);
            plugin.nutritionTypeRegistry().all().forEach(type -> {
                if (type.id().startsWith(prefix)) {
                    result.add(type.id());
                }
            });
            return result;
        }
        return result;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.messageService().send(sender, "general.reloading");
        plugin.reloadPluginStateAsync().thenRun(() -> runForSender(sender, () -> {
            plugin.messageService().send(sender, "general.reload_success");
            plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of(
                    "recipes", totalRecipeCount(),
                    "resources", 1
            )));
        }));
        return true;
    }

    private void runForSender(CommandSender sender, Runnable task) {
        ExecutionDispatcher dispatcher = plugin.executionDispatcher();
        if (dispatcher == null) {
            task.run();
            return;
        }
        if (sender instanceof Player player) {
            dispatcher.runEntity(plugin, player, task, task);
            return;
        }
        dispatcher.runGlobal(plugin, task);
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
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        return switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
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
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2 || !"reindex".equalsIgnoreCase(args[1])) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        if (plugin.stationStateStore() == null) {
            plugin.messageService().sendRaw(sender, "<red>Station state store is not ready.</red>");
            return true;
        }
        plugin.messageService().sendRaw(sender, "<gray>Rebuilding station location index...</gray>");
        plugin.stationStateStore().reindexAsync().thenAccept(report -> runForSender(sender, () -> plugin.messageService().sendRaw(sender,
                "<green>Station index rebuilt:</green> <gray>legacy_yaml=</gray>" + report.legacyYamlStates()
                        + " <gray>loaded_pdc=</gray>" + report.loadedPdcStates()
                        + " <gray>total_indexed=</gray>" + report.totalIndexedStates())));
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
        lines.put("help", plugin.messageService().message("command.help.desc.help"));
        lines.put("reload", plugin.messageService().message("command.help.desc.reload"));
        lines.put("inspect hand|block", plugin.messageService().message("command.help.desc.inspect"));
        lines.put("station reindex", "rebuild station storage index");
        lines.put("nutrition get|set|add|remove", plugin.messageService().message("command.help.desc.nutrition"));
        lines.put("debug [player|module|on|off]", plugin.messageService().message("command.help.desc.debug"));
        lines.forEach((name, description) -> plugin.messageService().sendRaw(
                sender,
                plugin.messageService().message("command.help.line", Map.of("cmd", name, "desc", description))
        ));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.footer"));
    }

    private boolean handleNutrition(CommandSender sender, String[] args) {
        if (plugin.nutritionService() == null) {
            plugin.messageService().send(sender, "nutrition.disabled");
            return true;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
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
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        // /ec nutrition get [player] [type]
        Player target;
        String typeArg;
        if (args.length >= 3 && org.bukkit.Bukkit.getPlayerExact(args[2]) != null) {
            target = org.bukkit.Bukkit.getPlayerExact(args[2]);
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
                    "type", emaki.jiuwu.craft.corelib.text.Texts.normalizeId(typeArg),
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
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        // /ec nutrition <set|add|remove> <player> <type> <value>
        if (args.length < 5) {
            plugin.messageService().send(sender, "nutrition.usage");
            return true;
        }
        Player target = org.bukkit.Bukkit.getPlayerExact(args[2]);
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
        emaki.jiuwu.craft.cooking.model.NutritionOperationResult result = switch (action) {
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
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }
}
