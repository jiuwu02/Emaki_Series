package emaki.jiuwu.craft.attribute.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.AttributePermissions;
import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.config.DamageCauseRule;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.CombatSupport;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class AttributeCommand implements TabExecutor {

    private final EmakiAttributePlugin plugin;
    private final AttributeService attributeService;
    private final EmakiScheduling scheduling;
    private final AttributePointsCommand pointsCommand;
    private final AttributeDiagnosticsCommand diagnosticsCommand;

    public AttributeCommand(EmakiAttributePlugin plugin, AttributeService attributeService) {
        this(plugin, attributeService, null);
    }

    public AttributeCommand(EmakiAttributePlugin plugin,
            AttributeService attributeService,
            EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.attributeService = attributeService;
        this.scheduling = scheduling;
        this.pointsCommand = new AttributePointsCommand(plugin, attributeService);
        this.diagnosticsCommand = new AttributeDiagnosticsCommand(plugin, attributeService);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" ->
                handleReload(sender);
            case "resync" ->
                handleResync(sender, args);
            case "preview" ->
                handlePreview(sender, args);
            case "dump" ->
                diagnosticsCommand.handleDump(sender, args);
            case "debug" ->
                diagnosticsCommand.handleDebug(sender, args);
            case "source" ->
                diagnosticsCommand.handleSource(sender, args);
            case "trace" ->
                diagnosticsCommand.handleTrace(sender, args);
            case "points" ->
                pointsCommand.handlePoints(sender, args);
            case "lint" ->
                handleLint(sender);
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            default -> {
                messages().send(sender, "command.unknown", Map.of("label", label));
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String candidate : List.of("help", "reload", "resync", "preview", "dump", "debug", "source", "trace", "points", "lint")) {
                if (candidate.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(candidate);
                }
            }
            return result;
        }
        if (args.length >= 2 && "points".equalsIgnoreCase(args[0])) {
            return completePoints(sender, args);
        }
        if (args.length == 2 && "resync".equalsIgnoreCase(args[0])) {
            result.addAll(CommandTabHelper.completeOnlinePlayers(args[1]));
            result.add("all");
            return result;
        }
        if (args.length == 2 && "dump".equalsIgnoreCase(args[0])) {
            result.addAll(CommandTabHelper.completeOnlinePlayers(args[1]));
            return result;
        }
        if (args.length == 3 && "dump".equalsIgnoreCase(args[0])) {
            if ("sources".startsWith(args[2].toLowerCase(Locale.ROOT))) {
                result.add("sources");
            }
            if ("json".startsWith(args[2].toLowerCase(Locale.ROOT))) {
                result.add("json");
            }
            return result;
        }
        if (args.length == 4 && "dump".equalsIgnoreCase(args[0]) && "sources".equalsIgnoreCase(args[2])) {
            completeAttributeIds(result, args[3]);
            return result;
        }
        if (args.length == 2 && "source".equalsIgnoreCase(args[0])) {
            result.addAll(CommandTabHelper.completeOnlinePlayers(args[1]));
            return result;
        }
        if (args.length == 3 && "source".equalsIgnoreCase(args[0])) {
            completeAttributeIds(result, args[2]);
            return result;
        }
        if (args.length == 2 && "trace".equalsIgnoreCase(args[0])) {
            for (String sub : List.of("last", "list", "export", "clear")) {
                if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 3 && "trace".equalsIgnoreCase(args[0])) {
            result.addAll(CommandTabHelper.completeOnlinePlayers(args[2]));
            return result;
        }
        if (args.length == 2 && "preview".equalsIgnoreCase(args[0])) {
            result.addAll(CommandTabHelper.completeOnlinePlayers(args[1]));
            for (String slot : previewSlots()) {
                if (slot.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(slot);
                }
            }
            return result;
        }
        if (args.length == 3 && "preview".equalsIgnoreCase(args[0])) {
            for (String slot : previewSlots()) {
                if (slot.startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    result.add(slot);
                }
            }
            return result;
        }
        if (args.length >= 2 && "debug".equalsIgnoreCase(args[0])) {
            return completeDebug(args);
        }
        return result;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(AttributePermissions.RELOAD) && !sender.hasPermission(AttributePermissions.ADMIN)) {
            messages().send(sender, "command.reload.no_permission");
            return true;
        }
        messages().send(sender, "command.reload.started");
        long startTime = System.currentTimeMillis();
        plugin.reloadPluginStateAsync(true, message -> runGlobal(() -> messages().sendRaw(sender, message)))
                .thenRun(() -> messages().send(sender, "command.reload.success"))
                .thenRun(() -> messages().send(sender, "command.reload.summary", Map.of(
                        "attributes", attributeService.attributeRegistry().all().size(),
                        "damage_types", attributeService.damageTypeRegistry().all().size(),
                        "profiles", attributeService.defaultProfileRegistry().all().size()
                )))
                .thenRun(() -> messages().sendRaw(sender, "<gray>重载耗时: <white>" + (System.currentTimeMillis() - startTime) + "ms</white></gray>"))
                .exceptionally(throwable -> {
                    runGlobal(() -> messages().send(sender, "command.reload.failed", Map.of(
                            "error", CombatSupport.rootCauseMessage(throwable)
                    )));
                    return null;
                });
        return true;
    }

    private void runGlobal(Runnable task) {
        if (task == null) {
            return;
        }
        EmakiScheduling sched = scheduling != null ? scheduling : plugin.scheduling();
        if (sched != null) {
            sched.runGlobal(plugin, task);
            return;
        }
        task.run();
    }

    private boolean handleResync(CommandSender sender, String[] args) {
        if (!sender.hasPermission(AttributePermissions.RESYNC) && !sender.hasPermission(AttributePermissions.ADMIN)) {
            messages().send(sender, "command.resync.no_permission");
            return true;
        }
        if (args.length < 2) {
            if (sender instanceof Player player) {
                attributeService.resyncPlayer(player);
                messages().send(sender, "command.resync.self_success");
                return true;
            }
            messages().send(sender, "command.resync.console_usage");
            return true;
        }
        if ("all".equalsIgnoreCase(args[1])) {
            attributeService.resyncAllPlayers();
            messages().send(sender, "command.resync.all_success");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages().send(sender, "command.resync.player_not_found", Map.of("player", args[1]));
            return true;
        }
        attributeService.resyncPlayer(target);
        messages().send(sender, "command.resync.player_success", Map.of("player", target.getName()));
        return true;
    }

    private boolean handlePreview(CommandSender sender, String[] args) {
        if (!sender.hasPermission(AttributePermissions.DEBUG) && !sender.hasPermission(AttributePermissions.ADMIN)) {
            messages().send(sender, "command.preview.no_permission");
            return true;
        }
        PreviewRequest request = resolvePreviewRequest(sender, args);
        if (request == null) {
            return true;
        }
        ItemStack itemStack = previewItem(request.player(), request.slot());
        if (itemStack == null || itemStack.getType().isAir()) {
            messages().send(sender, "command.preview.no_item");
            return true;
        }
        AttributeSnapshot snapshot = attributeService.collectItemSnapshot(itemStack);
        messages().send(sender, "command.preview.item", Map.of(
                "item", request.player().getName() + " / " + request.slot()
        ));
        messages().sendRaw(sender, buildPreviewNameMessage(itemStack));
        messages().send(sender, "command.preview.signature", Map.of(
                "signature", snapshot == null || Texts.isBlank(snapshot.sourceSignature()) ? "-" : snapshot.sourceSignature()
        ));
        messages().send(sender, "command.preview.values", Map.of(
                "values", formatPreviewValues(snapshot)
        ));
        return true;
    }

    private boolean handleLint(CommandSender sender) {
        if (!sender.hasPermission(AttributePermissions.RELOAD) && !sender.hasPermission(AttributePermissions.ADMIN)) {
            messages().send(sender, "command.lint.no_permission");
            return true;
        }
        messages().sendRaw(sender, messages().message("command.lint.header"));
        reportLintSection(sender, "配置", collectConfigIssues());
        reportLintSection(sender, "属性定义", plugin.attributeRegistry().issues());
        reportLintSection(sender, "伤害类型", plugin.damageTypeRegistry().issues());
        reportLintSection(sender, "默认属性配置", plugin.defaultProfileRegistry().issues());
        reportLintSection(sender, "Lore 格式", plugin.loreFormatRegistry().issues());
        reportLintSection(sender, "属性预设", plugin.presetRegistry().issues());
        reportLintSection(sender, "属性读取条件", plugin.pdcReadRuleLoader().issues());
        messages().sendRaw(sender, messages().message("command.lint.footer"));
        return true;
    }

    private String buildPreviewNameMessage(ItemStack itemStack) {
        return messages().message("command.preview.name_label")
                + "<white>"
                + ItemTextBridge.displayWithItemHoverText(itemStack)
                + "</white>";
    }

    private void sendHelp(CommandSender sender) {
        messages().sendRaw(sender, messages().message("command.help.header"));
        messages().send(sender, "command.help.reload");
        messages().send(sender, "command.help.resync");
        messages().send(sender, "command.help.preview");
        messages().send(sender, "command.help.dump");
        messages().send(sender, "command.help.debug");
        messages().send(sender, "command.help.source");
        messages().send(sender, "command.help.trace");
        messages().send(sender, "command.help.points");
        messages().send(sender, "command.help.lint");
        messages().sendRaw(sender, messages().message("command.help.footer"));
    }

    private MessageService messages() {
        return plugin.messageService();
    }

    private List<String> completePoints(CommandSender sender, String[] args) {
        List<String> result = new ArrayList<>();
        boolean admin = sender.hasPermission(AttributePermissions.POINTS_ADMIN) || sender.hasPermission(AttributePermissions.ADMIN);
        if (args.length == 2) {
            for (String sub : List.of("view", "open", "add", "reset", "grant", "set", "setreset")) {
                if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            if (List.of("view", "open", "reset").contains(sub) || admin && List.of("grant", "set", "setreset").contains(sub)) {
                result.addAll(CommandTabHelper.completeOnlinePlayers(args[2]));
                return result;
            }
            if ("add".equals(sub)) {
                completeParentAttributeIds(result, args[2]);
                if (admin) {
                    result.addAll(CommandTabHelper.completeOnlinePlayers(args[2]));
                }
                return distinctMatching(result, args[2]);
            }
        }
        if (args.length == 4 && "add".equals(sub)) {
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target != null) {
                completeParentAttributeIds(result, args[3]);
            }
            return result;
        }
        if (args.length == 4 && "reset".equals(sub) && "free".startsWith(args[3].toLowerCase(Locale.ROOT))) {
            result.add("free");
        }
        return result;
    }

    private List<String> completeDebug(String[] args) {
        List<String> result = new ArrayList<>();
        String[] debugArgs = Arrays.copyOfRange(args, 1, args.length);
        if (debugArgs.length == 1) {
            result.addAll(plugin.debugCommand().tabComplete(debugArgs));
            result.addAll(CommandTabHelper.completeOnlinePlayers(debugArgs[0]));
            return distinctMatching(result, debugArgs[0]);
        }
        if (debugArgs.length == 2) {
            Player target = Bukkit.getPlayerExact(debugArgs[0]);
            if (target != null) {
                for (String mode : List.of("on", "off", "toggle")) {
                    if (mode.startsWith(debugArgs[1].toLowerCase(Locale.ROOT))) {
                        result.add(mode);
                    }
                }
                return result;
            }
        }
        result.addAll(plugin.debugCommand().tabComplete(debugArgs));
        return result;
    }

    private List<String> distinctMatching(List<String> values, String prefix) {
        String lowerPrefix = Texts.toStringSafe(prefix).toLowerCase(Locale.ROOT);
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                distinct.add(value);
            }
        }
        return new ArrayList<>(distinct);
    }

    private void completeAttributeIds(List<String> result, String prefix) {
        String lowerPrefix = Texts.toStringSafe(prefix).toLowerCase(Locale.ROOT);
        for (String id : attributeService.attributeRegistry().all().keySet()) {
            if (id != null && id.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                result.add(id);
            }
        }
    }

    private void completeParentAttributeIds(List<String> result, String prefix) {
        String lowerPrefix = Texts.toStringSafe(prefix).toLowerCase(Locale.ROOT);
        for (AttributeDefinition definition : attributeService.parentAttributeService().parentAttributes()) {
            if (definition.id().toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                result.add(definition.id());
            }
        }
    }

    private PreviewRequest resolvePreviewRequest(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            Player player = Bukkit.getPlayerExact(args[1]);
            if (player != null) {
                String slot = args.length >= 3 ? normalizePreviewSlot(args[2]) : "main_hand";
                if (slot == null) {
                    messages().send(sender, "command.preview.invalid_slot", Map.of("slot", args[2]));
                    return null;
                }
                return new PreviewRequest(player, slot);
            }
            if (!(sender instanceof Player self)) {
                messages().send(sender, "command.preview.player_not_found", Map.of("player", args[1]));
                return null;
            }
            String slot = normalizePreviewSlot(args[1]);
            if (slot == null) {
                messages().send(sender, "command.preview.player_not_found", Map.of("player", args[1]));
                return null;
            }
            return new PreviewRequest(self, slot);
        }
        if (sender instanceof Player player) {
            return new PreviewRequest(player, "main_hand");
        }
        messages().send(sender, "command.preview.console_usage");
        return null;
    }

    private String normalizePreviewSlot(String raw) {
        String normalized = Texts.toStringSafe(raw).trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "main_hand", "mainhand", "hand" -> "main_hand";
            case "off_hand", "offhand" -> "off_hand";
            case "helmet" -> "helmet";
            case "chestplate", "chest" -> "chestplate";
            case "leggings", "legs" -> "leggings";
            case "boots" -> "boots";
            default -> null;
        };
    }

    private List<String> previewSlots() {
        return List.of("main_hand", "off_hand", "helmet", "chestplate", "leggings", "boots");
    }

    private ItemStack previewItem(Player player, String slot) {
        if (player == null || slot == null) {
            return null;
        }
        return switch (slot) {
            case "main_hand" -> player.getInventory().getItemInMainHand();
            case "off_hand" -> player.getInventory().getItemInOffHand();
            case "helmet" -> player.getInventory().getHelmet();
            case "chestplate" -> player.getInventory().getChestplate();
            case "leggings" -> player.getInventory().getLeggings();
            case "boots" -> player.getInventory().getBoots();
            default -> null;
        };
    }

    private String formatPreviewValues(AttributeSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Double> entry : diagnosticsCommand.orderedDumpValues(snapshot)) {
            if (entry.getValue() == null || Double.compare(entry.getValue(), 0D) == 0) {
                continue;
            }
            var definition = attributeService.attributeRegistry().resolve(entry.getKey());
            String displayName = definition == null ? entry.getKey() : definition.displayName();
            lines.add(displayName + "=" + Numbers.formatNumber(entry.getValue(), "0.##"));
        }
        return lines.isEmpty() ? "没有非零属性" : String.join(", ", lines);
    }

    private void reportLintSection(CommandSender sender, String name, List<String> issues) {
        if (issues == null || issues.isEmpty()) {
            messages().send(sender, "command.lint.ok", Map.of("name", name));
            return;
        }
        messages().send(sender, "command.lint.issues", Map.of("name", name, "count", issues.size()));
        for (String issue : issues) {
            messages().sendRaw(sender, issue);
        }
    }

    private List<String> collectConfigIssues() {
        List<String> issues = new ArrayList<>();
        if (plugin.damageTypeRegistry().resolve(plugin.configModel().defaultDamageType()) == null) {
            issues.add("default_damage_type 指向了未加载的伤害类型: " + plugin.configModel().defaultDamageType());
        }
        if (plugin.damageTypeRegistry().resolve(plugin.configModel().projectileDamageType()) == null) {
            issues.add("projectile_damage_type 指向了未加载的伤害类型: " + plugin.configModel().projectileDamageType());
        }
        if (plugin.configModel().vanillaEventDamageEnabled()
                && plugin.damageTypeRegistry().resolve(plugin.configModel().vanillaEventDamageType()) == null) {
            issues.add("vanilla_event_damage.damage_type 指向了未加载的伤害类型: " + plugin.configModel().vanillaEventDamageType());
        }
        for (DamageCauseRule rule : plugin.configModel().allowedDamageCauses()) {
            if (rule == null || !rule.enabled() || !rule.hasDamageType()) {
                continue;
            }
            if (plugin.damageTypeRegistry().resolve(rule.damageTypeId()) != null) {
                continue;
            }
            issues.add("allowed_damage_causes 中的 " + rule.cause() + " 指向了未加载的伤害类型: " + rule.damageTypeId());
        }
        return issues;
    }

    private record PreviewRequest(Player player, String slot) {

    }
}
