package emaki.jiuwu.craft.item;

import java.util.ArrayList;
import java.util.Arrays;
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

import emaki.jiuwu.craft.corelib.api.integration.PdcAttributePayloadSnapshot;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.service.ItemComponentInspector;

final class ItemCommandRouter implements TabExecutor {

    private static final String PERMISSION_USE = "emakiitem.use";
    private static final String PERMISSION_GIVE = "emakiitem.give";
    private static final String PERMISSION_INSPECT = "emakiitem.inspect";
    private static final String PERMISSION_RELOAD = "emakiitem.reload";
    private static final String PERMISSION_UPDATE = "emakiitem.update";
    private static final String PERMISSION_DEBUG = "emakiitem.debug";
    private static final String PERMISSION_ADMIN = "emakiitem.admin";
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
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            case "list" -> handleList(sender, args);
            case "give" -> handleGive(sender, args);
            case "inspect" -> handleInspect(sender, args);
            case "components", "component" -> handleComponents(sender, args);
            case "update" -> handleUpdate(sender, args);
            case "reload" -> handleReload(sender);
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
            for (String sub : List.of("help", "list", "give", "inspect", "components", "component", "update", "reload", "debug")) {
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
                case "give", "inspect", "update" -> completePlayers(result, args[1]);
                case "components", "component" -> {
                    completePlayers(result, args[1]);
                    if (sender instanceof Player player) {
                        completeComponentIds(result, player, args[1]);
                    }
                }
                default -> {
                }
            }
            return result;
        }
        if (args.length == 3 && ("components".equalsIgnoreCase(args[0]) || "component".equalsIgnoreCase(args[0]))) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                completeComponentIds(result, target, args[2]);
            }
            return result;
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            plugin.itemLoader().all().keySet().stream()
                    .filter(id -> id.startsWith(args[2].toLowerCase(java.util.Locale.ROOT)))
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
        plugin.actionService().execute(target, definition, "give", Map.of("amount", amount), itemStack);
        plugin.updateService().updatePlayerItems(target, "give");
        plugin.setService().refreshEquippedSets(target, "give");
        plugin.scheduleAttributeEquipmentSync(target);
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
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "update_version", "value", plugin.identifier().updateVersion(held))));
        EmakiItemDefinition definition = Texts.isBlank(id) ? null : plugin.itemLoader().get(id);
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "exists", "value", definition != null)));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "attributes", "value", inspectAttributes(held))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "attribute_meta", "value", inspectAttributeMeta(held))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "skills", "value", inspectSkills(held))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "definition_signature", "value", plugin.identifier().definitionSignature(held))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "set_id", "value", valueOrDash(plugin.identifier().setId(held)))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "set_piece", "value", valueOrDash(plugin.identifier().setPiece(held)))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "set_active_count", "value", valueOrDash(plugin.identifier().setActiveCount(held)))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "set_total_count", "value", valueOrDash(plugin.identifier().setTotalCount(held)))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "set_active_thresholds", "value", valueOrDash(plugin.identifier().setActiveThresholds(held)))));
        return true;
    }

    private boolean handleComponents(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_INSPECT)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        ComponentTarget target = componentTarget(sender, args);
        if (target.player() == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        ItemStack held = target.player().getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            plugin.messageService().send(sender, "command.components.no_item", Map.of("player", target.player().getName()));
            return true;
        }
        Map<String, ItemComponentInspector.ComponentEntry> components = plugin.componentInspector().components(held);
        if (components.isEmpty()) {
            plugin.messageService().send(sender, "command.components.empty", Map.of("player", target.player().getName()));
            return true;
        }
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.components.header", Map.of(
                "player", target.player().getName(),
                "count", components.size()
        )));
        if (Texts.isNotBlank(target.componentId())) {
            String componentId = plugin.componentInspector().normalizeComponentId(target.componentId());
            ItemComponentInspector.ComponentEntry entry = components.get(componentId);
            if (entry == null) {
                plugin.messageService().send(sender, "command.components.not_found", Map.of("id", componentId));
                return true;
            }
            sendComponentEntry(sender, entry.id(), plugin.componentInspector().prettyJson(held, entry.id()));
            return true;
        }
        for (ItemComponentInspector.ComponentEntry entry : components.values()) {
            sendComponentEntry(sender, entry.id(), plugin.componentInspector().prettyJson(held, entry.id()));
        }
        return true;
    }

    private ComponentTarget componentTarget(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return new ComponentTarget(sender instanceof Player player ? player : null, "");
        }
        Player named = Bukkit.getPlayerExact(args[1]);
        if (named != null) {
            return new ComponentTarget(named, args.length >= 3 ? args[2] : "");
        }
        if (sender instanceof Player player) {
            return new ComponentTarget(player, args[1]);
        }
        return new ComponentTarget(null, "");
    }

    private void sendComponentEntry(CommandSender sender, String componentId, String json) {
        plugin.messageService().sendComponent(sender, MiniMessages.parse(componentHeader(componentId)));
        for (String line : Texts.toStringSafe(json).split("\\R", -1)) {
            plugin.messageService().sendComponent(sender, MiniMessages.parse("<dark_gray>|</dark_gray> <white>" + MiniMessages.escape(line) + "</white>"));
        }
    }

    private String componentHeader(String componentId) {
        String escapedId = MiniMessages.escape(componentId);
        return "<gray>- <hover:show_text:'<gray>组件 ID: <white>" + escapedId
                + "</white></gray><newline><dark_gray>点击复制组件 ID</dark_gray>'>"
                + "<click:copy_to_clipboard:'" + tagArgument(componentId) + "'><aqua>" + escapedId + "</aqua></click></hover></gray>";
    }

    private String tagArgument(String value) {
        return Texts.toStringSafe(value).replace("\\", "\\\\").replace("'", "\\'");
    }

    private boolean handleUpdate(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_UPDATE)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player self ? self : null);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        int changed = plugin.updateService().updatePlayerItems(target, "command");
        changed += plugin.setService().refreshEquippedSets(target, "command");
        if (changed > 0) {
            plugin.scheduleAttributeEquipmentSync(target);
        }
        plugin.messageService().send(sender, "general.update_success", Map.of("player", target.getName(), "count", changed));
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

    private String valueOrDash(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value);
        return Texts.isBlank(text) ? "-" : text;
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
        plugin.messageService().send(sender, "general.reloading");
        plugin.reloadPluginStateAsync().thenRun(() -> {
            plugin.messageService().send(sender, "general.reload_success");
            plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of("items", plugin.itemLoader().all().size())));
        });
        return true;
    }

    private void completePlayers(List<String> result, String prefix) {
        Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix.toLowerCase(java.util.Locale.ROOT)))
                .forEach(result::add);
    }

    private void completeComponentIds(List<String> result, Player player, String prefix) {
        String normalizedPrefix = Texts.toStringSafe(prefix).toLowerCase(java.util.Locale.ROOT);
        for (String id : plugin.componentInspector().ids(player.getInventory().getItemInMainHand())) {
            if (id.toLowerCase(java.util.Locale.ROOT).startsWith(normalizedPrefix) && !result.contains(id)) {
                result.add(id);
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.header"));
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("help", plugin.messageService().message("command.help.desc.help"));
        lines.put("list [page]", plugin.messageService().message("command.help.desc.list"));
        lines.put("give <player> <id> [amount]", plugin.messageService().message("command.help.desc.give"));
        lines.put("inspect [player]", plugin.messageService().message("command.help.desc.inspect"));
        lines.put("components [player] [component_id]", plugin.messageService().message("command.help.desc.components"));
        lines.put("update [player]", plugin.messageService().message("command.help.desc.update"));
        lines.put("reload", plugin.messageService().message("command.help.desc.reload"));
        lines.put("debug [player|module|on|off]", plugin.messageService().message("command.help.desc.debug"));
        lines.forEach((name, description) -> plugin.messageService().sendRaw(sender,
                plugin.messageService().message("command.help.line", Map.of("cmd", name, "desc", description))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.footer"));
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DEBUG) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }

    private record ComponentTarget(Player player, String componentId) {
    }
}
