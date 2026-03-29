package emaki.jiuwu.craft.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

final class ForgeCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakiforge";
    private static final String PERMISSION_BOOK = PERMISSION_ROOT + ".book";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";

    private final EmakiForgePlugin plugin;

    ForgeCommandRouter(EmakiForgePlugin plugin) {
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
            case "forge" -> handleForge(sender);
            case "book" -> handleBook(sender);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender, args);
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
            for (String sub : List.of("help", "forge", "book", "reload", "list")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2 && "list".equalsIgnoreCase(args[0])) {
            for (String sub : List.of("recipes", "blueprints", "materials")) {
                if (sub.startsWith(args[1].toLowerCase())) {
                    result.add(sub);
                }
            }
        }
        return result;
    }

    private boolean handleForge(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        return plugin.forgeGuiService().openGeneralForgeGui(player);
    }

    private boolean handleBook(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_BOOK) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        return plugin.recipeBookGuiService().openRecipeBook(player);
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.reloadPluginState(true);
        plugin.messageService().send(sender, "general.reload_success");
        plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of(
            "blueprints", plugin.blueprintLoader().all().size(),
            "materials", plugin.materialLoader().all().size(),
            "recipes", plugin.recipeLoader().all().size(),
            "guis", plugin.guiTemplateLoader().all().size()
        )));
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "recipes" -> {
                plugin.messageService().sendRaw(sender, plugin.messageService().message("command.list.recipes_header", Map.of("count", plugin.recipeLoader().all().size())));
                plugin.recipeLoader().all().forEach((id, recipe) ->
                    plugin.messageService().sendRaw(sender, plugin.messageService().message("command.list.recipe_line", Map.of("id", id, "name", recipe.displayName()))));
            }
            case "blueprints" -> {
                plugin.messageService().sendRaw(sender, plugin.messageService().message("command.list.blueprints_header", Map.of("count", plugin.blueprintLoader().all().size())));
                plugin.blueprintLoader().all().forEach((id, blueprint) ->
                    plugin.messageService().sendRaw(sender, plugin.messageService().message("command.list.blueprint_line", Map.of("id", id, "name", blueprint.displayName()))));
            }
            case "materials" -> {
                plugin.messageService().sendRaw(sender, plugin.messageService().message("command.list.materials_header", Map.of("count", plugin.materialLoader().all().size())));
                plugin.materialLoader().all().forEach((id, material) -> plugin.messageService().sendRaw(sender, plugin.messageService().message(
                    "command.list.material_line",
                    Map.of(
                        "id", id,
                        "name", material.displayName(),
                        "capacity", material.capacityCost(),
                        "bonus", material.forgeCapacityBonus()
                    )
                )));
            }
            default -> plugin.messageService().send(sender, "general.invalid_args");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.header"));
        Map<String, String> lines = Map.of(
            "forge", "打开独立锻造台",
            "book", "打开配方图鉴",
            "reload", "重载配置文件",
            "help", "显示帮助信息",
            "list <type>", "列出配置项"
        );
        lines.forEach((commandName, description) ->
            plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.line", Map.of("cmd", commandName, "desc", description))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.footer"));
    }
}
