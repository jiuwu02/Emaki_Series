package emaki.jiuwu.craft.item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.api.ItemStateSnapshot;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPdcCodec;
import emaki.jiuwu.craft.corelib.api.command.CommandKeys;

final class ItemCommandRouter implements TabExecutor {

    private static final String PERMISSION_USE = "emakiitem.use";
    private static final String PERMISSION_GIVE = "emakiitem.give";
    private static final String PERMISSION_BROWSE = "emakiitem.browse";
    private static final String PERMISSION_EDIT = "emakiitem.edit";
    static final String PERMISSION_INSPECT = "emakiitem.inspect";
    private static final String PERMISSION_RELOAD = "emakiitem.reload";
    private static final String PERMISSION_UPDATE = "emakiitem.update";
    private static final String PERMISSION_DEBUG = "emakiitem.debug";
    static final String PERMISSION_ADMIN = "emakiitem.admin";

    private final EmakiItemPlugin plugin;
    private final EmakiScheduling scheduling;
    private final ItemCommandExecutors executors;
    private final ItemComponentsCommand componentsCommand;
    private final ItemMigrationCommand migrationCommand;

    ItemCommandRouter(EmakiItemPlugin plugin,
            EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.scheduling = scheduling;
        this.executors = new ItemCommandExecutors(plugin, scheduling);
        this.componentsCommand = new ItemComponentsCommand(plugin);
        this.migrationCommand = new ItemMigrationCommand(plugin, executors);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelpPage(sender, helpRoot(label), Arrays.asList(args).subList(1, args.length));
                yield true;
            }
            case "list" -> handleList(sender, args);
            case "browse" -> handleBrowse(sender, args);
            case "edit" -> handleEdit(sender, args);
            case "give" -> handleGive(sender, args);
            case "inspect" -> handleInspect(sender, args);
            case "components", "component" -> componentsCommand.handleComponents(sender, args);
            case "repair" -> handleRepair(sender);
            case "state" -> handleState(sender, args);
            case "update" -> handleUpdate(sender, args);
            case "alias" -> migrationCommand.handleAlias(sender, args);
            case "migrate" -> migrationCommand.handleMigrate(sender, args);
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
            return CommandTabHelper.completeSubcommands(
                    List.of("help", "list", "browse", "edit", "give", "inspect", "components", "component",
                            "repair", "state", "update", "alias", "migrate", "reload", "debug"), args[0]);
        }
        if (args.length >= 2 && "debug".equalsIgnoreCase(args[0])) {
            List<String> debugSuggestions = new ArrayList<>(
                    plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length))
            );
            if (args.length == 2 && "stats".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                debugSuggestions.add("stats");
            }
            return debugSuggestions.stream().distinct().toList();
        }
        if (args.length == 2 && "alias".equalsIgnoreCase(args[0])) {
            for (String sub : List.of("list", "add", "remove")) {
                if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2 && "migrate".equalsIgnoreCase(args[0])) {
            for (String sub : List.of("id", "inventory")) {
                if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2 && "state".equalsIgnoreCase(args[0])) {
            for (String sub : List.of("query", "repair", "recompute")) {
                if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 3 && "state".equalsIgnoreCase(args[0])) {
            return CommandTabHelper.completeOnlinePlayers(args[2]);
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "give", "inspect", "update" -> result.addAll(CommandTabHelper.completeOnlinePlayers(args[1]));
                case "edit" -> plugin.itemLoader().all().keySet().stream()
                        .filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .forEach(result::add);
                case "list", "browse" -> result.addAll(CommandTabHelper.completeIntRange(args[1], 1, 10));
                case "components", "component" -> {
                    if ("yaml".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        result.add("yaml");
                    }
                    result.addAll(CommandTabHelper.completeOnlinePlayers(args[1]));
                    if (sender instanceof Player player) {
                        componentsCommand.completeComponentIds(result, player, args[1]);
                    }
                }
                default -> {
                }
            }
            return result;
        }
        if (args.length == 3 && ("components".equalsIgnoreCase(args[0]) || "component".equalsIgnoreCase(args[0]))) {
            if ("yaml".equalsIgnoreCase(args[1])) {
                result.addAll(CommandTabHelper.completeOnlinePlayers(args[2]));
                if (sender instanceof Player player) {
                    componentsCommand.completeComponentIds(result, player, args[2]);
                }
                return result;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                componentsCommand.completeComponentIds(result, target, args[2]);
            }
            return result;
        }
        if (args.length == 4
                && ("components".equalsIgnoreCase(args[0]) || "component".equalsIgnoreCase(args[0]))
                && "yaml".equalsIgnoreCase(args[1])) {
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target != null) {
                componentsCommand.completeComponentIds(result, target, args[3]);
            }
            return result;
        }
        if (args.length == 3 && "alias".equalsIgnoreCase(args[0]) && "remove".equalsIgnoreCase(args[1])) {
            plugin.aliasLoader().all().keySet().stream()
                    .filter(id -> id.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .forEach(result::add);
            return result;
        }
        if (args.length == 4 && "alias".equalsIgnoreCase(args[0]) && "add".equalsIgnoreCase(args[1])) {
            plugin.itemLoader().all().keySet().stream()
                    .filter(id -> id.startsWith(args[3].toLowerCase(Locale.ROOT)))
                    .forEach(result::add);
            return result;
        }
        if (args.length == 4 && "migrate".equalsIgnoreCase(args[0]) && "id".equalsIgnoreCase(args[1])) {
            plugin.itemLoader().all().keySet().stream()
                    .filter(id -> id.startsWith(args[3].toLowerCase(Locale.ROOT)))
                    .forEach(result::add);
            return result;
        }
        if (args.length == 5 && "migrate".equalsIgnoreCase(args[0]) && "id".equalsIgnoreCase(args[1])) {
            for (String mode : List.of("--dry-run", "--apply")) {
                if (mode.startsWith(args[4].toLowerCase(Locale.ROOT))) {
                    result.add(mode);
                }
            }
            return result;
        }
        if (args.length == 3 && "migrate".equalsIgnoreCase(args[0]) && "inventory".equalsIgnoreCase(args[1])) {
            if ("all".startsWith(args[2].toLowerCase(Locale.ROOT))) {
                result.add("all");
            }
            result.addAll(CommandTabHelper.completeOnlinePlayers(args[2]));
            return result;
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            plugin.itemLoader().all().keySet().stream()
                    .filter(id -> id.startsWith(args[2].toLowerCase(Locale.ROOT)))
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        int page = Math.max(1, Numbers.tryParseInt(args.length >= 2 ? args[1] : null, 1));
        List<String> ids = new ArrayList<>(plugin.itemLoader().all().keySet());
        ids.sort(String::compareTo);
        int pageSize = 10;
        int pages = GuiPagination.totalPages(ids.size(), pageSize);
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
                    "name", plugin.itemApi().catalog().displayName(id).orElse(id)
            )));
        }
        return true;
    }

    private boolean handleBrowse(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_BROWSE)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        int page = Math.max(1, Numbers.tryParseInt(args.length >= 2 ? args[1] : null, 1));
        if (!plugin.browserGuiService().openPackBrowser(player, page - 1)) {
            plugin.messageService().send(sender, "browser.gui_open_failed");
        }
        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_EDIT)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "editor.command.usage");
            return true;
        }
        String itemId = Texts.normalizeId(args[1]);
        if (plugin.itemLoader().get(itemId) == null) {
            plugin.messageService().send(sender, "editor.command.unknown_item", Map.of("id", itemId));
            return true;
        }
        if (plugin.editorModule().openItem(player, itemId, plugin.itemLoader().packId(itemId), 0) == null) {
            plugin.messageService().send(sender, "editor.open_failed", Map.of("id", itemId));
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_GIVE)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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

        int requestedAmount = args.length >= 4 ? Math.max(1, Numbers.tryParseInt(args[3], 1)) : 0;
        ItemStack itemStack = plugin.itemFactory().create(id, requestedAmount);
        EmakiItemDefinition definition = plugin.idResolver().resolveDefinition(id);
        if (itemStack == null || definition == null) {
            plugin.messageService().send(sender, "general.item_not_found", Map.of("id", id));
            return true;
        }
        executors.runForPlayer(target, "give", () -> {
            if (!target.isOnline()) {
                executors.runForSender(sender, () -> plugin.messageService().send(sender, "general.player_not_found"));
                return;
            }
            int amount = itemStack.getAmount();
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(itemStack);
            leftovers.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
            plugin.actionService().execute(target, definition, "give", Map.of("amount", amount), itemStack);
            plugin.updateService().updatePlayerItems(target, "give");
            plugin.setService().refreshEquippedSets(target, "give");
            plugin.scheduleAttributeEquipmentSync(target);
            executors.runForSender(sender, () -> plugin.messageService().send(sender, "general.give_success", Map.of(
                    "player", target.getName(),
                    "id", id,
                    "amount", amount
            )));
        });
        return true;
    }

    private boolean handleInspect(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_INSPECT)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "skill_triggers", "value", inspectSkillTriggers(held))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "definition_signature", "value", plugin.identifier().definitionSignature(held))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "set_id", "value", valueOrDash(plugin.identifier().setId(held)))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "set_piece", "value", valueOrDash(plugin.identifier().setPiece(held)))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "set_active_count", "value", valueOrDash(plugin.identifier().setActiveCount(held)))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "set_total_count", "value", valueOrDash(plugin.identifier().setTotalCount(held)))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "set_active_thresholds", "value", valueOrDash(plugin.identifier().setActiveThresholds(held)))));
        return true;
    }

    private boolean handleRepair(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_USE)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!plugin.repairGuiService().open(player)) {
            plugin.messageService().send(sender, "repair.gui_open_failed");
        }
        return true;
    }

    private boolean handleState(CommandSender sender, String[] args) {
        String operation = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "query";
        boolean mutating = "repair".equals(operation) || "recompute".equals(operation);
        String permission = mutating ? PERMISSION_ADMIN : PERMISSION_INSPECT;
        if (!sender.hasPermission(permission)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : (sender instanceof Player self ? self : null);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        return switch (operation) {
            case "query" -> sendStateQuery(sender, target);
            case "repair" -> runStateRepair(sender, target);
            case "recompute" -> runStateRecompute(sender, target);
            default -> {
                plugin.messageService().send(sender, "command.state.usage");
                yield true;
            }
        };
    }

    private boolean sendStateQuery(CommandSender sender, Player target) {
        ItemStack held = target.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            plugin.messageService().send(sender, "command.state.no_item", Map.of("player", target.getName()));
            return true;
        }
        ItemStateSnapshot snapshot = plugin.stateService().snapshot(held);
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.state.header", Map.of(
                "player", target.getName(),
                "count", snapshot.values().size()
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.state.meta", Map.of(
                "schema", snapshot.metadata().schemaVersion(),
                "revision", snapshot.metadata().revision(),
                "instance", valueOrDash(snapshot.metadata().instanceId()),
                "valid", snapshot.metadata().valid()
        )));
        snapshot.values().forEach((key, value) -> plugin.messageService().sendRaw(sender,
                plugin.messageService().message("command.state.line", Map.of(
                        "key", key.key(),
                        "type", key.type().name().toLowerCase(Locale.ROOT),
                        "value", String.valueOf(value)
                ))));
        return true;
    }

    private boolean runStateRepair(CommandSender sender, Player target) {
        executors.runForPlayer(target, "state_repair", () -> {
            ItemStack held = target.getInventory().getItemInMainHand();
            if (held == null || held.getType().isAir()) {
                executors.runForSender(sender, () -> plugin.messageService().send(sender, "command.state.no_item",
                        Map.of("player", target.getName())));
                return;
            }
            ItemStateSnapshot repaired = plugin.stateService().repair(held);
            target.getInventory().setItemInMainHand(held);
            executors.runForSender(sender, () -> plugin.messageService().send(sender, "command.state.repaired", Map.of(
                    "player", target.getName(),
                    "schema", repaired.metadata().schemaVersion(),
                    "valid", repaired.metadata().valid()
            )));
        });
        return true;
    }

    private boolean runStateRecompute(CommandSender sender, Player target) {
        executors.runForPlayer(target, "state_recompute", () -> {
            int changed = plugin.updateService().updatePlayerItems(target, "state_recompute");
            changed += plugin.setService().refreshEquippedSets(target, "state_recompute");
            if (changed > 0) {
                plugin.scheduleAttributeEquipmentSync(target);
            }
            int total = changed;
            executors.runForSender(sender, () -> plugin.messageService().send(sender, "command.state.recomputed", Map.of(
                    "player", target.getName(),
                    "count", total
            )));
        });
        return true;
    }

    private boolean handleUpdate(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_UPDATE)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player self ? self : null);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        executors.runForPlayer(target, "update", () -> {
            int changed = plugin.updateService().updatePlayerItems(target, "command");
            changed += plugin.setService().refreshEquippedSets(target, "command");
            if (changed > 0) {
                plugin.scheduleAttributeEquipmentSync(target);
            }
            int changedCount = changed;
            executors.runForSender(sender, () -> plugin.messageService().send(sender, "general.update_success", Map.of(
                    "player", target.getName(),
                    "count", changedCount
            )));
        });
        return true;
    }

    private String inspectAttributes(ItemStack itemStack) {
        Map<String, Double> attributes = plugin.pdcAttributeGateway().readAttributes(itemStack, "emakiitem");
        return attributes.isEmpty() ? "-" : attributes.toString();
    }

    private String inspectAttributeMeta(ItemStack itemStack) {
        Map<String, String> meta = plugin.pdcAttributeGateway().readMeta(itemStack, "emakiitem");
        return meta.isEmpty() ? "-" : meta.toString();
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
        return valueOrDash(EquipmentSkillPdcCodec.readRaw(itemStack).skillIds());
    }

    private String inspectSkillTriggers(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "-";
        }
        return valueOrDash(EquipmentSkillPdcCodec.readRaw(itemStack).boundTriggers());
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.messageService().send(sender, "general.reloading");
        long startTime = System.currentTimeMillis();
        plugin.reloadPluginStateAsync().thenRun(() -> executors.runForSender(sender, () -> {
            long elapsedMs = System.currentTimeMillis() - startTime;
            plugin.messageService().send(sender, "general.reload_success");
            plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of("items", plugin.itemLoader().all().size())));
            plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_elapsed", Map.of("elapsed_ms", elapsedMs)));
        }));
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sendHelpPage(sender, helpRoot(label), List.of());
    }

    private String helpRoot(String label) {
        return "/" + (label == null || label.isBlank() ? "emakiitem" : label);
    }

    private void sendHelpPage(CommandSender sender, String root, List<String> path) {
        if (path.isEmpty()) {
            sendHelpLines(sender, root, "command.help_header",
                    "command.help_entry_list", "command.help_entry_browse", "command.help_entry_edit",
                    "command.help_entry_give", "command.help_entry_inspect", "command.help_entry_components",
                    "command.help_entry_repair", "command.help_entry_state", "command.help_entry_update",
                    "command.help_entry_alias", "command.help_entry_migrate", "command.help_entry_reload",
                    "command.help_entry_debug", "command.help_entry_help");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(0).toLowerCase(Locale.ROOT)) {
            case "edit" -> sendEditItemHelp(sender, root);
            case "state" -> sendStateHelp(sender, root);
            case "alias" -> sendAliasHelp(sender, root, path);
            case "migrate" -> sendMigrateHelp(sender, root, path);
            case "debug" -> sendDebugHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of());
        }
    }

    private void sendEditItemHelp(CommandSender sender, String root) {
        sendHelpLine(sender, plugin.messageService().message("command.help_edit_header", Map.of("root", root)));
        List<String> itemIds = new ArrayList<>(plugin.itemLoader().all().keySet());
        itemIds.sort(String::compareTo);
        if (itemIds.isEmpty()) {
            sendHelpLine(sender, plugin.messageService().message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String itemId : itemIds) {
            sendValueEntry(sender, itemId,
                    plugin.messageService().message("command.help_value_desc_edit", Map.of("value", itemId)),
                    root + " edit " + itemId);
        }
        sendHelpFooter(sender);
    }

    private void sendStateHelp(CommandSender sender, String root) {
        sendHelpLines(sender, root, "command.help_state_header",
                "command.help_state_entry_query", "command.help_state_entry_repair",
                "command.help_state_entry_recompute");
        sendHelpFooter(sender);
    }

    private void sendAliasHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 2 && "remove".equalsIgnoreCase(path.get(1))) {
            sendHelpLine(sender, plugin.messageService().message("command.help_alias_remove_header", Map.of("root", root)));
            List<String> aliasIds = new ArrayList<>(plugin.aliasLoader().all().keySet());
            aliasIds.sort(String::compareTo);
            if (aliasIds.isEmpty()) {
                sendHelpLine(sender, plugin.messageService().message("command.help_value_empty"));
                sendHelpFooter(sender);
                return;
            }
            for (String aliasId : aliasIds) {
                sendValueEntry(sender, aliasId,
                        plugin.messageService().message("command.help_value_desc_alias_remove", Map.of("value", aliasId)),
                        root + " alias remove " + aliasId);
            }
            sendHelpFooter(sender);
            return;
        }
        sendHelpLines(sender, root, "command.help_alias_header",
                "command.help_alias_entry_list", "command.help_alias_entry_add", "command.help_alias_entry_remove");
        sendHelpFooter(sender);
    }

    private void sendMigrateHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 2 && "inventory".equalsIgnoreCase(path.get(1))) {
            if (path.size() >= 3 && "player".equalsIgnoreCase(path.get(2))) {
                sendPlayerHelp(sender, root, "migrate inventory player",
                        "command.help_migrate_inventory_player_header", "command.help_value_desc_migrate_inventory_player");
                return;
            }
            sendHelpLines(sender, root, "command.help_migrate_inventory_header",
                    "command.help_migrate_inventory_entry_all", "command.help_migrate_inventory_entry_player");
            sendHelpFooter(sender);
            return;
        }
        sendHelpLines(sender, root, "command.help_migrate_header",
                "command.help_migrate_entry_id", "command.help_migrate_entry_inventory");
        sendHelpFooter(sender);
    }

    private void sendDebugHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() < 2) {
            sendHelpLines(sender, root, "command.help_debug_header",
                    "command.help_debug_entry_status", "command.help_debug_entry_on", "command.help_debug_entry_off",
                    "command.help_debug_entry_stats", "command.help_debug_entry_player",
                    "command.help_debug_entry_module", "command.help_debug_entry_action");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(1).toLowerCase(Locale.ROOT)) {
            case "player" -> sendPlayerHelp(sender, root, "debug player",
                    "command.help_debug_player_header", "command.help_value_desc_debug_player");
            case "action" -> sendDebugActionHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of("debug"));
        }
    }

    private void sendDebugActionHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 3 && "run".equalsIgnoreCase(path.get(2))) {
            sendHelpLines(sender, root, "command.help_debug_action_run_header",
                    "command.help_debug_action_run_entry_shadow", "command.help_debug_action_run_entry_execute");
            sendHelpFooter(sender);
            return;
        }
        sendHelpLines(sender, root, "command.help_debug_action_header",
                "command.help_debug_action_entry_run", "command.help_debug_action_entry_check",
                "command.help_debug_action_entry_registry");
        sendHelpFooter(sender);
    }

    private void sendPlayerHelp(CommandSender sender, String root, String command, String headerKey, String descKey) {
        sendHelpLine(sender, plugin.messageService().message(headerKey, Map.of("root", root)));
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        names.sort(String::compareTo);
        if (names.isEmpty()) {
            sendHelpLine(sender, plugin.messageService().message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String name : names) {
            sendValueEntry(sender, name,
                    plugin.messageService().message(descKey, Map.of("value", name)),
                    root + " " + command + " " + name);
        }
        sendHelpFooter(sender);
    }

    private void sendValueEntry(CommandSender sender, String value, String desc, String run) {
        sendHelpLine(sender, plugin.messageService().message("command.help_value_entry", Map.of(
                "value", value,
                "desc", desc,
                "run", run)));
    }

    private void sendHelpLines(CommandSender sender, String root, String... keys) {
        Map<String, ?> replacements = Map.of("root", root);
        for (String key : keys) {
            sendHelpLine(sender, plugin.messageService().message(key, replacements));
        }
    }

    private void sendHelpLine(CommandSender sender, String line) {
        plugin.messageService().sendComponent(sender, plugin.messageService().render(line));
    }

    private void sendHelpFooter(CommandSender sender) {
        sendHelpLine(sender, plugin.messageService().message("command.help_footer"));
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DEBUG) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (args.length >= 2 && "stats".equalsIgnoreCase(args[1])) {
            var snapshot = plugin.refreshMetrics().snapshot();
            Map<String, Object> refreshStats = new LinkedHashMap<>();
            refreshStats.put("events", snapshot.events());
            refreshStats.put("skipped", snapshot.skippedEvents());
            refreshStats.put("batches", snapshot.batches());
            refreshStats.put("rejected", snapshot.rejectedBatches());
            refreshStats.put("coalesced", snapshot.coalesced());
            refreshStats.put("requested_local", snapshot.requestedLocal());
            refreshStats.put("requested_full", snapshot.requestedFull());
            refreshStats.put("update_local", snapshot.actualUpdateLocal());
            refreshStats.put("update_full", snapshot.actualUpdateFull());
            refreshStats.put("set_local", snapshot.actualSetLocal());
            refreshStats.put("set_full", snapshot.actualSetFull());
            plugin.messageService().sendRaw(sender, plugin.messageService().message("command.debug.stats.refresh", refreshStats));
            Map<String, Object> refreshWork = new LinkedHashMap<>();
            refreshWork.put("cache_hits", snapshot.cacheHits());
            refreshWork.put("cache_invalid", snapshot.cacheInvalid());
            refreshWork.put("update_scanned", snapshot.updateScannedSlots());
            refreshWork.put("set_scanned", snapshot.setScannedSlots());
            refreshWork.put("scanned", snapshot.scannedSlots());
            refreshWork.put("changed", snapshot.changed());
            refreshWork.put("conflicts", snapshot.conflicts());
            refreshWork.put("ledger_decodes", snapshot.ledgerDecodes());
            refreshWork.put("set_compiles", snapshot.setCompiles());
            refreshWork.put("elapsed_ms", snapshot.elapsedMillis());
            refreshWork.put("full_reasons", snapshot.fullReasons());
            plugin.messageService().sendRaw(sender, plugin.messageService().message("command.debug.stats.work", refreshWork));
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }

}
