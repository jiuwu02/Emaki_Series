package emaki.jiuwu.craft.strengthen;

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
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

final class StrengthenCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakistrengthen";
    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";

    private final EmakiStrengthenPlugin plugin;

    StrengthenCommandRouter(EmakiStrengthenPlugin plugin) {
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
            case "open" -> handleOpen(sender);
            case "reload" -> handleReload(sender);
            case "inspect" -> handleInspect(sender, args);
            case "refresh" -> handleRefresh(sender, args);
            case "setstar" -> handleSetStar(sender, args);
            case "clearcrack" -> handleClearCrack(sender);
            case "givecatalyst" -> handleGiveCatalyst(sender, args);
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
            for (String sub : List.of("help", "open", "reload", "inspect", "refresh", "setstar", "clearcrack", "givecatalyst")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "inspect", "refresh" -> completePlayers(result, args[1]);
                case "setstar" -> {
                    int maxStar = plugin.recipeLoader().all().values().stream()
                            .mapToInt(recipe -> recipe == null ? 0 : recipe.limits().maxStar())
                            .max()
                            .orElse(12);
                    for (int star = 0; star <= maxStar; star++) {
                        String value = Integer.toString(star);
                        if (value.startsWith(args[1])) {
                            result.add(value);
                        }
                    }
                }
                case "givecatalyst" -> plugin.recipeLoader().materialCatalog().keySet().stream()
                        .filter(id -> id.startsWith(args[1].toLowerCase()))
                        .forEach(result::add);
                default -> {
                }
            }
            return result;
        }
        if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "setstar" -> plugin.replaceLoader().all().keySet().stream()
                        .filter(id -> id.startsWith(args[2].toLowerCase()))
                        .forEach(result::add);
                case "givecatalyst" -> {
                    for (String amount : List.of("1", "8", "16", "32", "64")) {
                        if (amount.startsWith(args[2])) {
                            result.add(amount);
                        }
                    }
                }
                default -> {
                }
            }
            return result;
        }
        if (args.length == 4 && "givecatalyst".equalsIgnoreCase(args[0])) {
            completePlayers(result, args[3]);
        }
        return result;
    }

    private boolean handleOpen(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (!plugin.strengthenGuiService().open(player)) {
            plugin.messageService().send(sender, "gui.open_failed");
        }
        return true;
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
                "recipes", plugin.recipeLoader().all().size(),
                "materials", plugin.recipeLoader().materialCatalog().size(),
                "guis", plugin.guiTemplateLoader().all().size()
        )));
        return true;
    }

    private boolean handleInspect(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN) && !sender.hasPermission(PERMISSION_USE)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        Player player = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player self ? self : null);
        if (player == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        StrengthenState state = plugin.attemptService().readState(player.getInventory().getItemInMainHand());
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.header", Map.of("player", player.getName())));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "eligible", "value", state.eligible())));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "reason",
                "value", state.eligibleReason().isBlank() ? "-" : state.eligibleReason()
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "recipe",
                "value", state.recipeId().isBlank() ? "-" : state.recipeId()
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "source",
                "value", state.baseSource() == null ? "-" : ItemSourceUtil.toShorthand(state.baseSource())
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "star", "value", state.currentStar())));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "crack", "value", state.crackLevel())));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "first_reach",
                "value", state.firstReachFlags().isEmpty() ? "-" : state.firstReachFlags()
        )));
        return true;
    }

    private boolean handleRefresh(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        Player player = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player self ? self : null);
        if (player == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        plugin.refreshService().refreshPlayerInventory(player);
        plugin.messageService().send(sender, "command.refresh.success", Map.of("player", player.getName()));
        return true;
    }

    private boolean handleSetStar(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        Integer star = Numbers.tryParseInt(args[1], null);
        if (star == null) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        ItemStack rebuilt = plugin.attemptService().applyAdminState(
                player.getInventory().getItemInMainHand(),
                star,
                null,
                args.length >= 3 ? args[2] : null
        );
        if (rebuilt == null) {
            plugin.messageService().send(sender, "command.admin_state_failed");
            return true;
        }
        player.getInventory().setItemInMainHand(rebuilt);
        plugin.messageService().send(sender, "command.setstar.success", Map.of("star", star));
        return true;
    }

    private boolean handleClearCrack(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        ItemStack rebuilt = plugin.attemptService().applyAdminState(player.getInventory().getItemInMainHand(), null, 0, null);
        if (rebuilt == null) {
            plugin.messageService().send(sender, "command.admin_state_failed");
            return true;
        }
        player.getInventory().setItemInMainHand(rebuilt);
        plugin.messageService().send(sender, "command.clearcrack.success");
        return true;
    }

    private boolean handleGiveCatalyst(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        String materialToken = plugin.recipeLoader().resolveMaterialToken(args[1]);
        if (Texts.isBlank(materialToken)) {
            plugin.messageService().send(sender, "command.catalyst_not_found");
            return true;
        }
        Integer amount = args.length >= 3 ? Numbers.tryParseInt(args[2], null) : 1;
        if (amount == null || amount <= 0) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        Player target = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : (sender instanceof Player self ? self : null);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        ItemStack itemStack = createMaterialItem(materialToken, amount);
        if (itemStack == null) {
            plugin.messageService().send(sender, "command.catalyst_create_failed");
            return true;
        }
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(itemStack);
        leftover.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        plugin.messageService().send(sender, "command.givecatalyst.success", Map.of(
                "player", target.getName(),
                "material", materialToken,
                "amount", amount
        ));
        return true;
    }

    private ItemStack createMaterialItem(String materialToken, int amount) {
        return plugin.coreItemFactory().create(ItemSourceUtil.parse(materialToken), Math.max(1, amount));
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
        lines.put("open", "打开强化界面");
        lines.put("reload", "重载强化配置和 GUI");
        lines.put("inspect [player]", "查看手持物品强化状态");
        lines.put("refresh [player]", "刷新玩家背包中的强化层");
        lines.put("setstar <star> [recipe]", "直接设置主手物品星级");
        lines.put("clearcrack", "清除主手物品裂痕");
        lines.put("givecatalyst <id> [amount] [player]", "发放强化材料");
        lines.forEach((name, description) -> plugin.messageService().sendRaw(sender,
                plugin.messageService().message("command.help.line", Map.of("cmd", name, "desc", description))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.footer"));
    }
}
