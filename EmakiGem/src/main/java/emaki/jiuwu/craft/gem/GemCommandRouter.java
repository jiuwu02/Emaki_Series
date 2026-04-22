package emaki.jiuwu.craft.gem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.gem.service.GemGuiMode;

final class GemCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakigem";
    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";

    private final EmakiGemPlugin plugin;

    GemCommandRouter(EmakiGemPlugin plugin) {
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
            case "gui" -> handleGuiCommand(sender, args);
            case "reload" -> handleReload(sender);
            case "inspect" -> handleInspect(sender, args);
            case "open", "inlay", "extract", "upgrade" -> handleLegacyGuiAlias(sender);
            case "clearstate" -> handleClearState(sender);
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
            for (String sub : List.of("help", "gui", "reload", "inspect", "clearstate")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "gui" -> {
                    for (String sub : List.of("inlay", "extract", "open", "upgrade")) {
                        if (sub.startsWith(args[1].toLowerCase())) {
                            result.add(sub);
                        }
                    }
                }
                case "inspect" -> completePlayers(result, args[1]);
                default -> {
                }
            }
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
        plugin.reloadPluginState(true);
        plugin.messageService().send(sender, "general.reload_success");
        plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of(
                "gems", plugin.gemLoader().all().size(),
                "items", plugin.gemItemLoader().all().size(),
                "guis", plugin.guiTemplateLoader().all().size()
        )));
        return true;
    }

    private boolean handleGui(CommandSender sender, GemGuiMode mode) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (!plugin.gemGuiService().open(player, mode)) {
            plugin.messageService().send(sender, "gui.open_failed");
        }
        return true;
    }

    private boolean handleGuiCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "inlay" -> handleGui(sender, GemGuiMode.INLAY);
            case "extract" -> handleGui(sender, GemGuiMode.EXTRACT);
            case "open" -> handleGui(sender, GemGuiMode.OPEN_SOCKET);
            case "upgrade" -> handleGui(sender, GemGuiMode.UPGRADE);
            default -> {
                plugin.messageService().send(sender, "general.invalid_args");
                yield true;
            }
        };
    }

    private boolean handleInspect(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        Player player = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player self ? self : null);
        if (player == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(itemStack);
        GemState state = plugin.stateService().resolveState(itemStack);
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.header", Map.of("player", player.getName())));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "item_definition",
                "value", itemDefinition == null ? "-" : itemDefinition.id()
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "identified_source",
                "value", plugin.itemMatcher().identifyItem(itemStack) == null ? "-" : ItemSourceUtil.toShorthand(plugin.itemMatcher().identifyItem(itemStack))
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "opened_slots",
                "value", state == null ? "-" : state.openedSlotIndexes()
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "socket_assignments",
                "value", state == null ? "-" : state.socketAssignments().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue().token())
                        .toList()
        )));
        return true;
    }

    private boolean handleLegacyGuiAlias(CommandSender sender) {
        plugin.messageService().send(sender, "command.gui_only");
        return true;
    }

    private boolean handleClearState(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        if (!plugin.stateService().hasStoredLayer(itemStack)) {
            plugin.messageService().send(sender, "command.clearstate.no_layer");
            return true;
        }
        ItemStack rebuilt = plugin.stateService().clearGemLayer(itemStack);
        if (rebuilt == null) {
            plugin.messageService().send(sender, "command.clearstate.apply_failed");
            return true;
        }
        player.getInventory().setItemInMainHand(rebuilt);
        plugin.messageService().send(sender, "command.clearstate.success");
        return true;
    }

    private void completePlayers(List<String> result, String prefix) {
        Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
                .forEach(result::add);
    }

    private void sendHelp(CommandSender sender) {
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.header"));
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("help", "显示帮助信息");
        lines.put("gui [inlay|extract|open|upgrade]", "打开对应的宝石 GUI");
        lines.put("reload", "重载宝石配置与资源");
        lines.put("inspect [player]", "查看主手装备的宝石状态");
        lines.put("clearstate", "移除主手物品上的宝石层");
        lines.forEach((name, description) -> plugin.messageService().sendRaw(sender,
                plugin.messageService().message("command.help.line", Map.of("cmd", name, "desc", description))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.footer"));
    }
}
