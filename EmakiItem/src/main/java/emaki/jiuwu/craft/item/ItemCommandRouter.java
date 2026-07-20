package emaki.jiuwu.craft.item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.model.EmakiItemAlias;
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
    private static final NamespacedKey SKILL_TRIGGERS_KEY = new NamespacedKey("emaki_skills", "item.skills.triggers");

    private final EmakiItemPlugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final ThreadOwnership threadOwnership;

    ItemCommandRouter(EmakiItemPlugin plugin,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
        this.threadOwnership = threadOwnership;
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
            case "repair" -> handleRepair(sender);
            case "update" -> handleUpdate(sender, args);
            case "alias" -> handleAlias(sender, args);
            case "migrate" -> handleMigrate(sender, args);
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
            for (String sub : List.of("help", "list", "give", "inspect", "components", "component", "repair", "update", "alias", "migrate", "reload", "debug")) {
                if (sub.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length >= 2 && "debug".equalsIgnoreCase(args[0])) {
            return plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 2 && "alias".equalsIgnoreCase(args[0])) {
            for (String sub : List.of("list", "add", "remove")) {
                if (sub.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2 && "migrate".equalsIgnoreCase(args[0])) {
            for (String sub : List.of("id", "inventory")) {
                if (sub.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "give", "inspect", "update" -> completePlayers(result, args[1]);
                case "components", "component" -> {
                    if ("yaml".startsWith(args[1].toLowerCase(java.util.Locale.ROOT))) {
                        result.add("yaml");
                    }
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
            if ("yaml".equalsIgnoreCase(args[1])) {
                completePlayers(result, args[2]);
                if (sender instanceof Player player) {
                    completeComponentIds(result, player, args[2]);
                }
                return result;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                completeComponentIds(result, target, args[2]);
            }
            return result;
        }
        if (args.length == 4
                && ("components".equalsIgnoreCase(args[0]) || "component".equalsIgnoreCase(args[0]))
                && "yaml".equalsIgnoreCase(args[1])) {
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target != null) {
                completeComponentIds(result, target, args[3]);
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
        // 未显式提供数量时传 0（哨兵），交由物品工厂回退到 definition 配置的默认数量。
        int requestedAmount = args.length >= 4 ? Math.max(1, Numbers.tryParseInt(args[3], 1)) : 0;
        ItemStack itemStack = plugin.itemFactory().create(id, requestedAmount);
        EmakiItemDefinition definition = plugin.idResolver().resolveDefinition(id);
        if (itemStack == null || definition == null) {
            plugin.messageService().send(sender, "general.item_not_found", Map.of("id", id));
            return true;
        }
        runForPlayer(target, "give", () -> {
            if (!target.isOnline()) {
                runForSender(sender, () -> plugin.messageService().send(sender, "general.player_not_found"));
                return;
            }
            int amount = itemStack.getAmount();
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(itemStack);
            leftovers.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
            plugin.actionService().execute(target, definition, "give", Map.of("amount", amount), itemStack);
            plugin.updateService().updatePlayerItems(target, "give");
            plugin.setService().refreshEquippedSets(target, "give");
            plugin.scheduleAttributeEquipmentSync(target);
            runForSender(sender, () -> plugin.messageService().send(sender, "general.give_success", Map.of(
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
            String output = target.yaml()
                    ? plugin.componentInspector().prettyYaml(held, entry.id())
                    : plugin.componentInspector().prettyJson(held, entry.id());
            sendComponentEntry(sender, entry.id(), output);
            return true;
        }
        if (target.yaml()) {
            sendComponentOutput(sender, plugin.componentInspector().prettyYaml(held));
            return true;
        }
        for (ItemComponentInspector.ComponentEntry entry : components.values()) {
            sendComponentEntry(sender, entry.id(), plugin.componentInspector().prettyJson(held, entry.id()));
        }
        return true;
    }

    private ComponentTarget componentTarget(CommandSender sender, String[] args) {
        int index = 1;
        boolean yaml = args.length > index && "yaml".equalsIgnoreCase(args[index]);
        if (yaml) {
            index++;
        }
        if (args.length <= index) {
            return new ComponentTarget(sender instanceof Player player ? player : null, "", yaml);
        }
        Player named = Bukkit.getPlayerExact(args[index]);
        if (named != null) {
            return new ComponentTarget(named, args.length > index + 1 ? args[index + 1] : "", yaml);
        }
        if (sender instanceof Player player) {
            return new ComponentTarget(player, args[index], yaml);
        }
        return new ComponentTarget(null, "", yaml);
    }

    private void sendComponentEntry(CommandSender sender, String componentId, String output) {
        plugin.messageService().sendComponent(sender, MiniMessages.parse(componentHeader(componentId)));
        sendComponentOutput(sender, output);
    }

    private void sendComponentOutput(CommandSender sender, String output) {
        for (String line : Texts.toStringSafe(output).split("\\R", -1)) {
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
        runForPlayer(target, "update", () -> {
            int changed = plugin.updateService().updatePlayerItems(target, "command");
            changed += plugin.setService().refreshEquippedSets(target, "command");
            if (changed > 0) {
                plugin.scheduleAttributeEquipmentSync(target);
            }
            int changedCount = changed;
            runForSender(sender, () -> plugin.messageService().send(sender, "general.update_success", Map.of(
                    "player", target.getName(),
                    "count", changedCount
            )));
        });
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

    private String inspectSkillTriggers(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "-";
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return "-";
        }
        String raw = itemMeta.getPersistentDataContainer().get(SKILL_TRIGGERS_KEY, PersistentDataType.STRING);
        return Texts.isBlank(raw) ? "-" : raw;
    }

    private boolean handleAlias(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            plugin.messageService().sendRaw(sender, "<gray>EmakiItem aliases: <white>" + plugin.aliasLoader().all().size() + "</white></gray>");
            for (EmakiItemAlias alias : plugin.aliasLoader().all().values()) {
                plugin.messageService().sendRaw(sender, "<gray>- <aqua>" + alias.oldId() + "</aqua> -> <green>" + alias.targetId() + "</green></gray>");
            }
            return true;
        }
        if ("add".equalsIgnoreCase(args[1]) && args.length >= 4) {
            String oldId = Texts.normalizeId(args[2]);
            String newId = Texts.normalizeId(args[3]);
            if (plugin.itemLoader().get(newId) == null) {
                plugin.messageService().send(sender, "general.item_not_found", Map.of("id", newId));
                return true;
            }
            plugin.aliasLoader().put(oldId, newId);
            plugin.itemFactory().clearCache();
            plugin.messageService().sendRaw(sender, "<green>Alias 已添加：</green> <aqua>" + oldId + "</aqua> -> <green>" + newId + "</green>");
            return true;
        }
        if ("remove".equalsIgnoreCase(args[1]) && args.length >= 3) {
            String oldId = Texts.normalizeId(args[2]);
            boolean removed = plugin.aliasLoader().remove(oldId);
            plugin.itemFactory().clearCache();
            plugin.messageService().sendRaw(sender, removed ? "<green>Alias 已删除：</green> <aqua>" + oldId + "</aqua>" : "<yellow>Alias 不存在：</yellow> <aqua>" + oldId + "</aqua>");
            return true;
        }
        plugin.messageService().sendRaw(sender, "<red>用法：</red> /ei alias list | add <old> <new> | remove <old>");
        return true;
    }

    private boolean handleMigrate(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length >= 5 && "id".equalsIgnoreCase(args[1])) {
            String oldId = Texts.normalizeId(args[2]);
            String newId = Texts.normalizeId(args[3]);
            String mode = args[4].toLowerCase(java.util.Locale.ROOT);
            try {
                if ("--dry-run".equals(mode)) {
                    Map<String, Object> preview = plugin.migrationService().preview(oldId, newId);
                    plugin.messageService().sendRaw(sender, "<gray>迁移预览：<aqua>" + oldId + "</aqua> -> <green>" + newId + "</green>，替换数 <white>" + preview.get("replacementCount") + "</white></gray>");
                    for (Object file : (List<?>) preview.getOrDefault("files", List.of())) {
                        plugin.messageService().sendRaw(sender, "<gray>- " + file + "</gray>");
                    }
                    return true;
                }
                if ("--apply".equals(mode)) {
                    Map<String, Object> result = plugin.migrationService().apply(oldId, newId, true, true);
                    plugin.aliasLoader().load();
                    plugin.itemFactory().clearCache();
                    plugin.messageService().sendRaw(sender, "<green>迁移完成：</green> 替换数 <white>" + result.get("replacementCount") + "</white>，并保留 alias。");
                    return true;
                }
            } catch (Exception exception) {
                plugin.messageService().sendRaw(sender, "<red>迁移失败：</red> " + MiniMessages.escape(exception.getMessage()));
                return true;
            }
        }
        if (args.length >= 3 && "inventory".equalsIgnoreCase(args[1])) {
            if ("all".equalsIgnoreCase(args[2])) {
                List<Player> targets = List.copyOf(Bukkit.getOnlinePlayers());
                if (targets.isEmpty()) {
                    plugin.messageService().sendRaw(sender, "<green>在线玩家背包迁移完成：</green> 0 件物品。");
                    return true;
                }
                AtomicInteger totalChanged = new AtomicInteger();
                AtomicInteger remaining = new AtomicInteger(targets.size());
                java.util.function.IntConsumer complete = changed -> {
                    totalChanged.addAndGet(changed);
                    if (remaining.decrementAndGet() == 0) {
                        runForSender(sender, () -> plugin.messageService().sendRaw(sender,
                                "<green>在线玩家背包迁移完成：</green> " + totalChanged.get() + " 件物品。"));
                    }
                };
                for (Player target : targets) {
                    boolean accepted = runForPlayer(target, "migrate_inventory", () -> {
                        int changed = target.isOnline() ? plugin.migrationService().migrateInventory(target) : 0;
                        int refreshed = target.isOnline() ? plugin.setService().refreshEquippedSets(target, "command") : 0;
                        if (changed + refreshed > 0) {
                            plugin.scheduleAttributeEquipmentSync(target);
                        }
                        complete.accept(changed);
                    });
                    if (!accepted) {
                        complete.accept(0);
                    }
                }
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                plugin.messageService().send(sender, "general.player_not_found");
                return true;
            }
            runForPlayer(target, "migrate_inventory", () -> {
                int changed = plugin.migrationService().migrateInventory(target);
                int refreshed = plugin.setService().refreshEquippedSets(target, "command");
                if (changed + refreshed > 0) {
                    plugin.scheduleAttributeEquipmentSync(target);
                }
                runForSender(sender, () -> plugin.messageService().sendRaw(sender,
                        "<green>背包迁移完成：</green> " + target.getName() + " / " + changed + " 件物品。"));
            });
            return true;
        }
        plugin.messageService().sendRaw(sender, "<red>用法：</red> /ei migrate id <old> <new> --dry-run|--apply 或 /ei migrate inventory <player|all>");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.messageService().send(sender, "general.reloading");
        plugin.reloadPluginStateAsync().thenRun(() -> runForSender(sender, () -> {
            plugin.messageService().send(sender, "general.reload_success");
            plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of("items", plugin.itemLoader().all().size())));
        }));
        return true;
    }

    private boolean runForPlayer(Player player, String operation, Runnable task) {
        if (player == null || task == null) {
            return false;
        }
        boolean owner = threadOwnership.isEntityOwned(player);
        debugCommandDomain(player, operation, owner ? "direct" : "scheduled", owner);
        if (owner) {
            task.run();
            return true;
        }
        boolean accepted = executionDispatcher.runEntity(plugin, player, task) != null;
        if (!accepted) {
            debugCommandDomain(player, operation, "rejected", false);
        }
        return accepted;
    }

    private void runForSender(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            if (threadOwnership.isEntityOwned(player)) {
                task.run();
            } else {
                executionDispatcher.runEntity(plugin, player, task);
            }
            return;
        }
        executionDispatcher.runGlobal(plugin, task);
    }

    private void debugCommandDomain(Player player, String operation, String stage, boolean owner) {
        var debugLogger = plugin.debugLogger();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        debugLogger.logRaw("set", player, "[DEBUG:SET_COMMAND] operation=" + Texts.toStringSafe(operation)
                + " stage=" + Texts.toStringSafe(stage)
                + " global_owner=" + threadOwnership.isGlobalOwned()
                + " owner=" + owner
                + " thread=" + Thread.currentThread().getName());
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
        lines.put("components [yaml] [player] [component_id]", plugin.messageService().message("command.help.desc.components"));
        lines.put("repair", plugin.messageService().message("command.help.desc.repair"));
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
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }

    private record ComponentTarget(Player player, String componentId, boolean yaml) {
    }
}
