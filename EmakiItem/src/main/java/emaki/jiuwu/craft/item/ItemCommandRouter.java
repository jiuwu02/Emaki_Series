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

final class ItemCommandRouter implements TabExecutor {

    private static final String PERMISSION_USE = "emakiitem.use";
    private static final String PERMISSION_GIVE = "emakiitem.give";
    private static final String PERMISSION_BROWSE = "emakiitem.browse";
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
            sendHelp(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            case "list" -> handleList(sender, args);
            case "browse" -> handleBrowse(sender, args);
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
            for (String sub : List.of("help", "list", "browse", "give", "inspect", "components", "component", "repair", "state", "update", "alias", "migrate", "reload", "debug")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
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
            plugin.messageService().send(sender, "general.no_permission");
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
            plugin.messageService().send(sender, "general.no_permission");
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
            plugin.messageService().send(sender, "general.no_permission");
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
            plugin.messageService().send(sender, "general.no_permission");
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
            plugin.messageService().send(sender, "general.no_permission");
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
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.messageService().send(sender, "general.reloading");
        long startTime = System.currentTimeMillis();
        plugin.reloadPluginStateAsync().thenRun(() -> executors.runForSender(sender, () -> {
            long elapsedMs = System.currentTimeMillis() - startTime;
            plugin.messageService().send(sender, "general.reload_success");
            plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of("items", plugin.itemLoader().all().size())));
            plugin.messageService().sendRaw(sender, "<gray>重载耗时: <white>" + elapsedMs + "ms</white></gray>");
        }));
        return true;
    }

    private void sendHelp(CommandSender sender) {
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.header"));
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("help", plugin.messageService().message("command.help.desc.help"));
        lines.put("list [page]", plugin.messageService().message("command.help.desc.list"));
        lines.put("browse [page]", plugin.messageService().message("command.help.desc.browse"));
        lines.put("give <player> <id> [amount]", plugin.messageService().message("command.help.desc.give"));
        lines.put("inspect [player]", plugin.messageService().message("command.help.desc.inspect"));
        lines.put("components [yaml] [player] [component_id]", plugin.messageService().message("command.help.desc.components"));
        lines.put("repair", plugin.messageService().message("command.help.desc.repair"));
        lines.put("state query|repair|recompute [player]", plugin.messageService().message("command.help.desc.state"));
        lines.put("update [player]", plugin.messageService().message("command.help.desc.update"));
        lines.put("alias list|add|remove", "管理物品 ID alias。");
        lines.put("migrate id|inventory", "预览或执行物品 ID 迁移。");
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
        if (args.length >= 2 && "stats".equalsIgnoreCase(args[1])) {
            var snapshot = plugin.refreshMetrics().snapshot();
            plugin.messageService().sendRaw(sender, "<gray>EmakiItem refresh stats:</gray>"
                    + " <white>events=" + snapshot.events()
                    + " skipped=" + snapshot.skippedEvents()
                    + " batches=" + snapshot.batches()
                    + " rejected=" + snapshot.rejectedBatches()
                    + " coalesced=" + snapshot.coalesced()
                    + " requested_local=" + snapshot.requestedLocal()
                    + " requested_full=" + snapshot.requestedFull()
                    + " update_local=" + snapshot.actualUpdateLocal()
                    + " update_full=" + snapshot.actualUpdateFull()
                    + " set_local=" + snapshot.actualSetLocal()
                    + " set_full=" + snapshot.actualSetFull() + "</white>");
            plugin.messageService().sendRaw(sender, "<gray>Refresh work:</gray>"
                    + " <white>cache_hits=" + snapshot.cacheHits()
                    + " cache_invalid=" + snapshot.cacheInvalid()
                    + " update_scanned=" + snapshot.updateScannedSlots()
                    + " set_scanned=" + snapshot.setScannedSlots()
                    + " scanned=" + snapshot.scannedSlots()
                    + " changed=" + snapshot.changed()
                    + " conflicts=" + snapshot.conflicts()
                    + " ledger_decodes=" + snapshot.ledgerDecodes()
                    + " set_compiles=" + snapshot.setCompiles()
                    + " elapsed_ms=" + snapshot.elapsedMillis()
                    + " full_reasons=" + snapshot.fullReasons() + "</white>");
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }

}
