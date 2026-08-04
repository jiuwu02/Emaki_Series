package emaki.jiuwu.craft.strengthen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;

final class StrengthenCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakistrengthen";
    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";

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
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            case "open" -> handleOpen(sender);
            case "reload" -> handleReload(sender);
            case "inspect" -> handleInspect(sender, args);
            case "refresh" -> handleRefresh(sender, args);
            case "setstar" -> handleSetStar(sender, args);
            case "clearstate" -> handleClearState(sender);
            case "clearcrack" -> handleClearCrack(sender);
            case "givecatalyst" -> handleGiveCatalyst(sender, args);
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
            for (String sub : List.of("help", "open", "reload", "inspect", "refresh", "setstar", "clearstate", "clearcrack", "givecatalyst", "debug")) {
                if (sub.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length >= 2 && "debug".equalsIgnoreCase(args[0])) {
            return plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "inspect", "refresh" -> result.addAll(CommandTabHelper.completeOnlinePlayers(args[1]));
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
                        .filter(id -> id.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                        .forEach(result::add);
                default -> {
                }
            }
            return result;
        }
        if (args.length == 3) {
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "setstar" -> plugin.recipeLoader().all().keySet().stream()
                        .filter(id -> id.startsWith(args[2].toLowerCase(java.util.Locale.ROOT)))
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
            result.addAll(CommandTabHelper.completeOnlinePlayers(args[3]));
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
        plugin.messageService().send(sender, "general.reloading");
        plugin.reloadPluginStateAsync(true).thenRun(() -> runForSender(sender, () -> {
            plugin.messageService().send(sender, "general.reload_success");
            plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of(
                    "recipes", plugin.recipeLoader().all().size(),
                    "materials", plugin.recipeLoader().materialCatalog().size(),
                    "guis", plugin.guiTemplateLoader().all().size()
            )));
        }));
        return true;
    }

    private void runForSender(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            plugin.executionDispatcher().runEntity(plugin, player, task);
            return;
        }
        plugin.executionDispatcher().runGlobal(plugin, task);
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
                "value", Texts.isBlank(state.baseSource()) ? "-" : state.baseSource()
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

    private boolean handleClearState(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (!plugin.attemptService().readState(player.getInventory().getItemInMainHand()).hasLayer()) {
            plugin.messageService().send(sender, "command.clearstate.no_layer");
            return true;
        }
        ItemStack rebuilt = plugin.attemptService().clearStrengthenLayer(player.getInventory().getItemInMainHand());
        if (rebuilt == null) {
            plugin.messageService().send(sender, "command.admin_state_failed");
            return true;
        }
        player.getInventory().setItemInMainHand(rebuilt);
        plugin.messageService().send(sender, "command.clearstate.success");
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
        InventoryItemUtil.giveOrDrop(target, itemStack);
        plugin.messageService().send(sender, "command.givecatalyst.success", Map.of(
                "player", target.getName(),
                "material", materialToken,
                "amount", amount
        ));
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DEBUG) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }

    private ItemStack createMaterialItem(String materialToken, int amount) {
        return plugin.coreItemFactory().create(ItemSourceUtil.parse(materialToken), Math.max(1, amount));
    }

    private void sendHelp(CommandSender sender) {
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.header"));
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("help", plugin.messageService().message("command.help.desc.help"));
        lines.put("open", plugin.messageService().message("command.help.desc.open"));
        lines.put("reload", plugin.messageService().message("command.help.desc.reload"));
        lines.put("inspect [player]", plugin.messageService().message("command.help.desc.inspect"));
        lines.put("refresh [player]", plugin.messageService().message("command.help.desc.refresh"));
        lines.put("setstar <star> [recipe]", plugin.messageService().message("command.help.desc.setstar"));
        lines.put("clearstate", plugin.messageService().message("command.help.desc.clearstate"));
        lines.put("clearcrack", plugin.messageService().message("command.help.desc.clearcrack"));
        lines.put("givecatalyst <id> [amount] [player]", plugin.messageService().message("command.help.desc.givecatalyst"));
        lines.put("debug <status|player|module|all> [...]", plugin.messageService().message("command.help.desc.debug"));
        lines.forEach((name, description) -> plugin.messageService().sendRaw(sender,
                plugin.messageService().message("command.help.line", Map.of("cmd", name, "desc", description))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.footer"));
    }
}
