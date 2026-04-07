package emaki.jiuwu.craft.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.forge.service.EditableResourceType;

final class ForgeCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakiforge";
    private static final String PERMISSION_BOOK = PERMISSION_ROOT + ".book";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";
    private static final String PERMISSION_EDITOR = PERMISSION_ROOT + ".editor";
    private static final String PERMISSION_EDITOR_DELETE = PERMISSION_ROOT + ".editor.delete";

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
            case "forge" ->
                handleForge(sender);
            case "book" ->
                handleBook(sender);
            case "reload" ->
                handleReload(sender);
            case "list" ->
                handleList(sender, args);
            case "editor" ->
                handleEditor(sender);
            case "edit" ->
                handleEdit(sender, args);
            case "create" ->
                handleCreate(sender, args);
            case "delete" ->
                handleDelete(sender, args);
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
            for (String sub : List.of("help", "forge", "book", "reload", "list", "editor", "edit", "create", "delete")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2 && List.of("list", "edit", "create", "delete").contains(args[0].toLowerCase())) {
            for (String sub : List.of("recipes", "blueprints", "materials", "recipe", "blueprint", "material")) {
                if (sub.startsWith(args[1].toLowerCase())) {
                    result.add(sub);
                }
            }
        }
        if (args.length == 3 && List.of("edit", "delete").contains(args[0].toLowerCase())) {
            EditableResourceType type = EditableResourceType.fromInput(args[1]);
            if (type != null) {
                for (String id : resourceIds(type)) {
                    if (id.startsWith(args[2].toLowerCase())) {
                        result.add(id);
                    }
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
                plugin.recipeLoader().all().forEach((id, recipe)
                        -> plugin.messageService().sendRaw(sender, plugin.messageService().message("command.list.recipe_line", Map.of("id", id, "name", recipe.displayName()))));
            }
            case "blueprints" -> {
                plugin.messageService().sendRaw(sender, plugin.messageService().message("command.list.blueprints_header", Map.of("count", plugin.blueprintLoader().all().size())));
                plugin.blueprintLoader().all().forEach((id, blueprint)
                        -> plugin.messageService().sendRaw(sender, plugin.messageService().message("command.list.blueprint_line", Map.of("id", id, "name", blueprint.displayName()))));
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
            default ->
                plugin.messageService().send(sender, "general.invalid_args");
        }
        return true;
    }

    private boolean handleEditor(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!hasEditorPermission(sender)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        plugin.editorGuiService().openIndex(player);
        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!hasEditorPermission(sender)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 3) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        EditableResourceType type = EditableResourceType.fromInput(args[1]);
        if (type == null) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        plugin.editorGuiService().openExisting(player, type, args[2]);
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!hasEditorPermission(sender)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        EditableResourceType type = EditableResourceType.fromInput(args[1]);
        if (type == null) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        plugin.editorGuiService().createNew(player, type, args.length >= 3 ? args[2] : null);
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_EDITOR_DELETE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 3) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        EditableResourceType type = EditableResourceType.fromInput(args[1]);
        if (type == null) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        plugin.editorGuiService().deleteDirect(player, type, args[2]);
        return true;
    }

    private boolean hasEditorPermission(CommandSender sender) {
        return sender.hasPermission(PERMISSION_EDITOR) || sender.hasPermission(PERMISSION_ADMIN);
    }

    private List<String> resourceIds(EditableResourceType type) {
        return switch (type) {
            case BLUEPRINT -> new ArrayList<>(plugin.blueprintLoader().all().keySet());
            case MATERIAL -> new ArrayList<>(plugin.materialLoader().all().keySet());
            case RECIPE -> new ArrayList<>(plugin.recipeLoader().all().keySet());
        };
    }

    private void sendHelp(CommandSender sender) {
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.header"));
        Map<String, String> lines = Map.of(
                "forge", "打开独立锻造台",
                "book", "打开配方图鉴",
                "reload", "重载配置文件",
                "help", "显示帮助信息",
                "list <type>", "列出配置项",
                "editor", "打开锻造资源编辑器",
                "edit <type> <id>", "编辑指定资源",
                "create <type> [id]", "创建新资源草稿",
                "delete <type> <id>", "删除指定资源"
        );
        lines.forEach((commandName, description)
                -> plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.line", Map.of("cmd", commandName, "desc", description))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.footer"));
    }
}
