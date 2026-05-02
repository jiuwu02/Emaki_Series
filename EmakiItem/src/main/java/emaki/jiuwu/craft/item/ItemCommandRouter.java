package emaki.jiuwu.craft.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.integration.PdcAttributePayloadSnapshot;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;

final class ItemCommandRouter implements TabExecutor {

    private static final String PERMISSION_USE = "emakiitem.use";
    private static final String PERMISSION_GIVE = "emakiitem.give";
    private static final String PERMISSION_INSPECT = "emakiitem.inspect";
    private static final String PERMISSION_RELOAD = "emakiitem.reload";
    private static final NamespacedKey SKILL_IDS_KEY = new NamespacedKey("emaki_skills", "item.skills.ids");

    private final EmakiItemPlugin plugin;

    ItemCommandRouter(EmakiItemPlugin plugin) {
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
            case "list" -> handleList(sender, args);
            case "give" -> handleGive(sender, args);
            case "inspect" -> handleInspect(sender, args);
            case "reload" -> handleReload(sender);
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
            for (String sub : List.of("help", "list", "give", "inspect", "reload")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "give", "inspect" -> completePlayers(result, args[1]);
                default -> {
                }
            }
            return result;
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            plugin.itemLoader().all().keySet().stream()
                    .filter(id -> id.startsWith(args[2].toLowerCase()))
                    .forEach(result::add);
            return result;
        }
        if (args.length == 4 && "give".equalsIgnoreCase(args[0])) {
            for (String amount : List.of("1", "8", "16", "32", "64")) {
                if (amount.startsWith(args[3])) {
                    result.add(amount);
                }
            }
        }
        return result;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_USE)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        int page = Math.max(1, Numbers.tryParseInt(args.length >= 2 ? args[1] : null, 1));
        List<String> ids = new ArrayList<>(plugin.itemLoader().all().keySet());
        ids.sort(String::compareTo);
        int pageSize = 10;
        int pages = Math.max(1, (int) Math.ceil(ids.size() / (double) pageSize));
        page = Math.min(page, pages);
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.list.header", Map.of(
                "page", page,
                "pages", pages,
                "count", ids.size()
        )));
        int start = (page - 1) * pageSize;
        for (int index = start; index < Math.min(ids.size(), start + pageSize); index++) {
            String id = ids.get(index);
            plugin.messageService().sendRaw(sender, plugin.messageService().message("command.list.line", Map.of(
                    "id", id,
                    "name", plugin.itemApi().displayName(id)
            )));
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_GIVE)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 3) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        String id = Texts.normalizeId(args[2]);
        int amount = Math.max(1, Numbers.tryParseInt(args.length >= 4 ? args[3] : null, 1));
        ItemStack itemStack = plugin.itemFactory().create(id, amount);
        EmakiItemDefinition definition = plugin.itemLoader().get(id);
        if (itemStack == null || definition == null) {
            plugin.messageService().send(sender, "general.item_not_found", Map.of("id", id));
            return true;
        }
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(itemStack);
        leftovers.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        plugin.actionService().execute(target, definition, "give", Map.of("amount", amount));
        plugin.messageService().send(sender, "general.give_success", Map.of(
                "player", target.getName(),
                "id", id,
                "amount", amount
        ));
        return true;
    }

    private boolean handleInspect(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_INSPECT)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player self ? self : null);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        ItemStack held = target.getInventory().getItemInMainHand();
        String id = plugin.identifier().identify(held);
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.header", Map.of("player", target.getName())));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "id", "value", Texts.isBlank(id) ? "-" : id)));
        Integer schemaVersion = plugin.identifier().schemaVersion(held);
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "schema_version", "value", schemaVersion == null ? "-" : schemaVersion)));
        EmakiItemDefinition definition = Texts.isBlank(id) ? null : plugin.itemLoader().get(id);
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "exists", "value", definition != null)));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "attributes", "value", inspectAttributes(held))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "attribute_meta", "value", inspectAttributeMeta(held))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "skills", "value", inspectSkills(held))));
        return true;
    }

    private String inspectAttributes(ItemStack itemStack) {
        PdcAttributePayloadSnapshot snapshot = plugin.pdcAttributeGateway().readAll(itemStack).get("emakiitem");
        return snapshot == null || snapshot.attributes().isEmpty() ? "-" : snapshot.attributes().toString();
    }

    private String inspectAttributeMeta(ItemStack itemStack) {
        PdcAttributePayloadSnapshot snapshot = plugin.pdcAttributeGateway().readAll(itemStack).get("emakiitem");
        return snapshot == null || snapshot.meta().isEmpty() ? "-" : snapshot.meta().toString();
    }

    private String inspectSkills(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "-";
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return "-";
        }
        String raw = itemMeta.getPersistentDataContainer().get(SKILL_IDS_KEY, PersistentDataType.STRING);
        return Texts.isBlank(raw) ? "-" : raw;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.reloadPluginState();
        plugin.messageService().send(sender, "general.reload_success");
        plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of("items", plugin.itemLoader().all().size())));
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
        lines.put("help", plugin.messageService().message("command.help.desc.help"));
        lines.put("list [page]", plugin.messageService().message("command.help.desc.list"));
        lines.put("give <player> <id> [amount]", plugin.messageService().message("command.help.desc.give"));
        lines.put("inspect [player]", plugin.messageService().message("command.help.desc.inspect"));
        lines.put("reload", plugin.messageService().message("command.help.desc.reload"));
        lines.forEach((name, description) -> plugin.messageService().sendRaw(sender,
                plugin.messageService().message("command.help.line", Map.of("cmd", name, "desc", description))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.footer"));
    }
}
