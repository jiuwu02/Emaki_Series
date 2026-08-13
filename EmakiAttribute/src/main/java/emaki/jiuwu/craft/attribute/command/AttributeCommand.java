package emaki.jiuwu.craft.attribute.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.AttributePermissions;
import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.config.DamageCauseRule;
import emaki.jiuwu.craft.attribute.model.AttributeContributionTrace;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.AttributeSourceTraceReport;
import emaki.jiuwu.craft.attribute.model.DamageTraceRecord;
import emaki.jiuwu.craft.attribute.model.ParentAttributeData;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.CombatSupport;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.util.Jsons;
import emaki.jiuwu.craft.attribute.service.ParentAttributeService;

public final class AttributeCommand implements TabExecutor {

    private final EmakiAttributePlugin plugin;
    private final AttributeService attributeService;
    private final EmakiScheduling scheduling;

    public AttributeCommand(EmakiAttributePlugin plugin, AttributeService attributeService) {
        this(plugin, attributeService, null);
    }

    public AttributeCommand(EmakiAttributePlugin plugin,
            AttributeService attributeService,
            EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.attributeService = attributeService;
        this.scheduling = scheduling;
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
                handleDump(sender, args);
            case "debug" ->
                handleDebug(sender, args);
            case "source" ->
                handleSource(sender, args);
            case "trace" ->
                handleTrace(sender, args);
            case "points" ->
                handlePoints(sender, args);
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
        plugin.reloadPluginStateAsync(true, message -> runGlobal(() -> messages().sendRaw(sender, message)))
                .thenRun(() -> messages().send(sender, "command.reload.success"))
                .thenRun(() -> messages().send(sender, "command.reload.summary", Map.of(
                        "attributes", attributeService.attributeRegistry().all().size(),
                        "damage_types", attributeService.damageTypeRegistry().all().size(),
                        "profiles", attributeService.defaultProfileRegistry().all().size()
                )))
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

    private boolean handleDump(CommandSender sender, String[] args) {
        if (!sender.hasPermission(AttributePermissions.DEBUG) && !sender.hasPermission(AttributePermissions.ADMIN)) {
            messages().send(sender, "command.dump.no_permission");
            return true;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                messages().send(sender, "command.dump.player_not_found", Map.of("player", args[1]));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            messages().send(sender, "command.dump.console_usage");
            return true;
        }
        if (args.length >= 3 && "sources".equalsIgnoreCase(args[2])) {
            sendSourceTrace(sender, target, args.length >= 4 ? args[3] : "");
            return true;
        }
        AttributeSnapshot snapshot = attributeService.collectCombatSnapshot(target);
        messages().send(sender, "command.dump.player", Map.of("player", target.getName()));
        sendDumpSignature(sender, snapshot);
        sendDumpValues(sender, snapshot);
        if (args.length >= 3 && "json".equalsIgnoreCase(args[2])) {
            messages().sendRaw(sender, Jsons.stringify(attributeService.attributeTraceService().trace(target, "").toMap()));
        }
        for (Map.Entry<String, ResourceState> entry : dumpResources(target).entrySet()) {
            ResourceState state = entry.getValue();
            messages().send(sender, "command.dump.resource_line", Map.of(
                    "resource", entry.getKey(),
                    "default_max", state.defaultMax(),
                    "bonus_max", state.bonusMax(),
                    "current_max", state.currentMax(),
                    "current", state.currentValue()
            ));
        }
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(AttributePermissions.DEBUG) && !sender.hasPermission(AttributePermissions.ADMIN)) {
            messages().send(sender, "command.debug.no_permission");
            return true;
        }
        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                String mode = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "toggle";
                if (List.of("on", "off", "toggle").contains(mode)) {
                    boolean enabled = switch (mode) {
                        case "on" -> attributeService.setCombatDebug(target, true);
                        case "off" -> attributeService.setCombatDebug(target, false);
                        default -> attributeService.toggleCombatDebug(target);
                    };
                    messages().send(sender, "command.debug.combat_trace_toggled", Map.of(
                            "player", MiniMessages.escape(target.getName()),
                            "state", messages().message(enabled
                                    ? "command.debug.combat_trace_on"
                                    : "command.debug.combat_trace_off")
                    ));
                    return true;
                }
                messages().send(sender, "command.debug.player_usage");
                return true;
            }
            if (List.of("status", "on", "off", "player", "module").contains(args[1].toLowerCase(Locale.ROOT))) {
                return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
            }
            messages().send(sender, "command.debug.player_not_found", Map.of("player", args[1]));
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }

    private boolean handleSource(CommandSender sender, String[] args) {
        if (!sender.hasPermission(AttributePermissions.DEBUG) && !sender.hasPermission(AttributePermissions.ADMIN)) {
            messages().send(sender, "command.dump.no_permission");
            return true;
        }
        if (args.length < 3) {
            messages().send(sender, "command.source.usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages().send(sender, "command.dump.player_not_found", Map.of("player", args[1]));
            return true;
        }
        sendSourceTrace(sender, target, args[2]);
        return true;
    }

    private boolean handleTrace(CommandSender sender, String[] args) {
        if (!sender.hasPermission(AttributePermissions.DEBUG) && !sender.hasPermission(AttributePermissions.ADMIN)) {
            messages().send(sender, "command.debug.no_permission");
            return true;
        }
        if (args.length < 3) {
            messages().send(sender, "command.trace.usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages().send(sender, "command.dump.player_not_found", Map.of("player", args[2]));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "last" -> {
                DamageTraceRecord record = attributeService.damageTraceService().last(target.getUniqueId());
                sendDamageTrace(sender, record, false);
                yield true;
            }
            case "list" -> {
                List<DamageTraceRecord> records = attributeService.damageTraceService().list(target.getUniqueId());
                messages().send(sender, "command.trace.list_header", Map.of(
                        "player", MiniMessages.escape(target.getName()),
                        "count", records.size()
                ));
                for (DamageTraceRecord record : records) {
                    messages().sendRaw(sender, formatTraceSummary(record));
                }
                yield true;
            }
            case "export" -> {
                DamageTraceRecord record = attributeService.damageTraceService().last(target.getUniqueId());
                sendDamageTrace(sender, record, true);
                yield true;
            }
            case "clear" -> {
                boolean cleared = attributeService.damageTraceService().clear(target.getUniqueId());
                messages().send(sender, cleared ? "command.trace.cleared" : "command.trace.clear_empty");
                yield true;
            }
            default -> {
                messages().send(sender, "command.trace.unknown_action", Map.of(
                        "action", MiniMessages.escape(action)
                ));
                yield true;
            }
        };
    }

    private boolean handlePoints(CommandSender sender, String[] args) {
        if (!(sender instanceof Player self) && args.length < 3) {
            messages().send(sender, "command.points.console_usage");
            return true;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "view";
        return switch (action) {
            case "view", "info" -> handlePointsView(sender, args);
            case "open", "gui" -> handlePointsOpen(sender, args);
            case "add" -> handlePointsAdd(sender, args);
            case "set" -> handlePointsSet(sender, args);
            case "reset" -> handlePointsReset(sender, args);
            case "grant" -> handlePointsGrant(sender, args);
            case "setreset" -> handlePointsSetReset(sender, args);
            default -> {
                messages().send(sender, "command.points.usage");
                yield true;
            }
        };
    }

    private boolean handlePointsView(CommandSender sender, String[] args) {
        Player target = resolvePointsTarget(sender, args.length >= 3 ? args[2] : null);
        if (target == null) {
            return true;
        }
        ParentAttributeData data = attributeService.parentAttributeService().data(target);
        messages().send(sender, "command.points.header", Map.of(
                "player", target.getName(),
                "available", data.availablePoints(),
                "reset", data.resetPoints(),
                "allocated", data.allocatedTotal()
        ));
        for (AttributeDefinition definition : attributeService.parentAttributeService().parentAttributes()) {
            messages().send(sender, "command.points.line", Map.of(
                    "attribute", definition.id(),
                    "display_name", definition.displayName(),
                    "points", data.allocation(definition.id())
            ));
        }
        return true;
    }

    private boolean handlePointsOpen(CommandSender sender, String[] args) {
        Player target = resolvePointsTarget(sender, args.length >= 3 ? args[2] : null);
        if (target == null) {
            return true;
        }
        if (sender != target && !sender.hasPermission(AttributePermissions.POINTS_ADMIN) && !sender.hasPermission(AttributePermissions.ADMIN)) {
            messages().send(sender, "command.points.no_permission");
            return true;
        }
        if (!plugin.attributePointsGuiService().open(target)) {
            messages().send(sender, "command.points.gui_failed");
        }
        return true;
    }

    private boolean handlePointsAdd(CommandSender sender, String[] args) {
        Player target;
        String attributeId;
        int amount;
        boolean admin = sender.hasPermission(AttributePermissions.POINTS_ADMIN) || sender.hasPermission(AttributePermissions.ADMIN);
        if (admin && args.length >= 5) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                messages().send(sender, "command.points.player_not_found", Map.of("player", args[2]));
                return true;
            }
            attributeId = args[3];
            amount = parseInt(args[4], 1);
        } else if (admin && args.length == 4 && Bukkit.getPlayerExact(args[2]) != null) {
            target = Bukkit.getPlayerExact(args[2]);
            attributeId = args[3];
            amount = 1;
        } else if (sender instanceof Player player) {
            if (!sender.hasPermission(AttributePermissions.POINTS) && !sender.hasPermission(AttributePermissions.ADMIN)) {
                messages().send(sender, "command.points.no_permission");
                return true;
            }
            if (args.length < 3) {
                messages().send(sender, "command.points.usage");
                return true;
            }
            target = player;
            attributeId = args[2];
            amount = args.length >= 4 ? parseInt(args[3], 1) : 1;
        } else {
            messages().send(sender, "command.points.admin_usage");
            return true;
        }
        AttributeDefinition definition = attributeService.parentAttributeService().parentAttribute(attributeId);
        if (definition == null) {
            messages().send(sender, "command.points.unknown_attribute", Map.of("attribute", attributeId));
            return true;
        }
        var result = attributeService.parentAttributeService().allocate(target, definition.id(), amount);
        if (result == ParentAttributeService.AllocateResult.SUCCESS) {
            messages().send(sender, "command.points.add_success", Map.of("player", target.getName(), "attribute", definition.displayName(), "amount", Math.max(1, amount)));
        } else {
            messages().send(sender, "command.points.add_failed", Map.of("reason", result.name().toLowerCase(Locale.ROOT)));
        }
        return true;
    }

    private boolean handlePointsSet(CommandSender sender, String[] args) {
        if (!requirePointsAdmin(sender)) {
            return true;
        }
        if (args.length < 4) {
            messages().send(sender, "command.points.admin_usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages().send(sender, "command.points.player_not_found", Map.of("player", args[2]));
            return true;
        }
        int amount = parseInt(args[3], 0);
        attributeService.parentAttributeService().setAvailablePoints(target, amount);
        messages().send(sender, "command.points.set_success", Map.of("player", target.getName(), "amount", amount));
        return true;
    }

    private boolean handlePointsGrant(CommandSender sender, String[] args) {
        if (!requirePointsAdmin(sender)) {
            return true;
        }
        if (args.length < 4) {
            messages().send(sender, "command.points.admin_usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages().send(sender, "command.points.player_not_found", Map.of("player", args[2]));
            return true;
        }
        int amount = parseInt(args[3], 0);
        attributeService.parentAttributeService().addAvailablePoints(target, amount);
        messages().send(sender, "command.points.grant_success", Map.of("player", target.getName(), "amount", amount));
        return true;
    }

    private boolean handlePointsSetReset(CommandSender sender, String[] args) {
        if (!requirePointsAdmin(sender)) {
            return true;
        }
        if (args.length < 4) {
            messages().send(sender, "command.points.admin_usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages().send(sender, "command.points.player_not_found", Map.of("player", args[2]));
            return true;
        }
        int amount = parseInt(args[3], 0);
        attributeService.parentAttributeService().setResetPoints(target, amount);
        messages().send(sender, "command.points.set_reset_success", Map.of("player", target.getName(), "amount", amount));
        return true;
    }

    private boolean handlePointsReset(CommandSender sender, String[] args) {
        Player target = resolvePointsTarget(sender, args.length >= 3 ? args[2] : null);
        if (target == null) {
            return true;
        }
        boolean adminReset = sender != target || (args.length >= 4 && "free".equalsIgnoreCase(args[3]));
        if (adminReset && !sender.hasPermission(AttributePermissions.POINTS_ADMIN) && !sender.hasPermission(AttributePermissions.ADMIN)) {
            messages().send(sender, "command.points.no_permission");
            return true;
        }
        var result = attributeService.parentAttributeService().reset(target, !adminReset);
        if (result == ParentAttributeService.ResetResult.SUCCESS) {
            messages().send(sender, "command.points.reset_success", Map.of("player", target.getName()));
        } else {
            messages().send(sender, "command.points.reset_failed", Map.of("reason", result.name().toLowerCase(Locale.ROOT)));
        }
        return true;
    }

    private Player resolvePointsTarget(CommandSender sender, String name) {
        if (Texts.isBlank(name)) {
            if (sender instanceof Player player) {
                if (!sender.hasPermission(AttributePermissions.POINTS) && !sender.hasPermission(AttributePermissions.ADMIN)) {
                    messages().send(sender, "command.points.no_permission");
                    return null;
                }
                return player;
            }
            messages().send(sender, "command.points.console_usage");
            return null;
        }
        if (!sender.hasPermission(AttributePermissions.POINTS_ADMIN) && !sender.hasPermission(AttributePermissions.ADMIN)) {
            messages().send(sender, "command.points.no_permission");
            return null;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            messages().send(sender, "command.points.player_not_found", Map.of("player", name));
        }
        return target;
    }

    private boolean requirePointsAdmin(CommandSender sender) {
        if (sender.hasPermission(AttributePermissions.POINTS_ADMIN) || sender.hasPermission(AttributePermissions.ADMIN)) {
            return true;
        }
        messages().send(sender, "command.points.no_permission");
        return false;
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(Texts.toStringSafe(raw));
        } catch (NumberFormatException exception) {
            return fallback;
        }
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

    /**
     * Sends the source signature.
     *
     * <p>A player gets the compact hover form; the console gets the value expanded onto its own indented
     * line, because a console cannot hover and would otherwise only ever see the label.</p>
     */
    private void sendDumpSignature(CommandSender sender, AttributeSnapshot snapshot) {
        boolean blank = snapshot == null || snapshot.sourceSignature() == null
                || snapshot.sourceSignature().isBlank();
        if (supportsHover(sender)) {
            String hoverText = blank
                    ? messages().message("command.dump.signature_empty")
                    : "<yellow>" + MiniMessages.escape(snapshot.sourceSignature()) + "</yellow>";
            messages().sendRaw(sender, MiniMessages.withHoverText(
                    messages().message("command.dump.signature"), hoverText));
            return;
        }
        messages().sendRaw(sender, messages().message("command.dump.signature_header"));
        if (blank) {
            messages().sendRaw(sender, messages().message("command.dump.signature_empty_line"));
            return;
        }
        messages().send(sender, "command.dump.signature_line", Map.of(
                "signature", MiniMessages.escape(snapshot.sourceSignature())));
    }

    /**
     * Sends the non-zero attribute values.
     *
     * <p>Same split as {@link #sendDumpSignature}: hover for players, one indented line per attribute for
     * the console. Both paths read the same ordered list, so the two views cannot drift.</p>
     */
    private void sendDumpValues(CommandSender sender, AttributeSnapshot snapshot) {
        List<String> hoverLines = new ArrayList<>();
        List<Map.Entry<String, Double>> shown = new ArrayList<>();
        for (Map.Entry<String, Double> entry : orderedDumpValues(snapshot)) {
            String attributeId = entry.getKey();
            Double value = entry.getValue();
            if (attributeId == null || value == null || Double.compare(value, 0D) == 0) {
                continue;
            }
            shown.add(entry);
            hoverLines.add("<aqua>" + MiniMessages.escape(displayNameOf(attributeId)) + "</aqua>"
                    + "<dark_gray> (</dark_gray>"
                    + "<white>" + MiniMessages.escape(attributeId) + "</white>"
                    + "<dark_gray>): </dark_gray>"
                    + "<yellow>" + MiniMessages.escape(Numbers.formatNumber(value, "0.##")) + "</yellow>");
        }
        if (supportsHover(sender)) {
            String hoverText = hoverLines.isEmpty()
                    ? messages().message("command.dump.values_empty")
                    : String.join("\n", hoverLines);
            messages().sendRaw(sender, MiniMessages.withHoverText(
                    messages().message("command.dump.values"), hoverText));
            return;
        }
        messages().sendRaw(sender, messages().message("command.dump.values_header"));
        if (shown.isEmpty()) {
            messages().sendRaw(sender, messages().message("command.dump.values_empty_line"));
            return;
        }
        for (Map.Entry<String, Double> entry : shown) {
            messages().send(sender, "command.dump.values_line", Map.of(
                    "attribute", MiniMessages.escape(displayNameOf(entry.getKey())),
                    "attribute_id", MiniMessages.escape(entry.getKey()),
                    "value", MiniMessages.escape(Numbers.formatNumber(entry.getValue(), "0.##"))));
        }
    }

    /**
     * {@return whether this sender can read hover text}
     *
     * <p>Only a real player can hover. The console renders a component's plain text, so hover content is
     * silently dropped there.</p>
     */
    private boolean supportsHover(CommandSender sender) {
        return sender instanceof Player;
    }

    private String displayNameOf(String attributeId) {
        var definition = attributeService.attributeRegistry().resolve(attributeId);
        return definition == null ? attributeId : definition.displayName();
    }

    private List<Map.Entry<String, Double>> orderedDumpValues(AttributeSnapshot snapshot) {
        Map<String, Double> values = snapshot == null ? Map.of() : snapshot.values();
        List<Map.Entry<String, Double>> ordered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (var definition : attributeService.attributeRegistry().all().values()) {
            if (definition == null) {
                continue;
            }
            Double value = values.get(definition.id());
            if (value == null) {
                continue;
            }
            ordered.add(Map.entry(definition.id(), value));
            seen.add(definition.id());
        }
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            if (entry.getKey() == null || seen.contains(entry.getKey())) {
                continue;
            }
            ordered.add(entry);
        }
        return ordered;
    }

    private Map<String, ResourceState> dumpResources(Player player) {
        Map<String, ResourceState> resources = new LinkedHashMap<>();
        attributeService.resourceDefinitions().forEach((id, definition) -> {
            ResourceState state = attributeService.readResourceState(player, id);
            if (state != null) {
                resources.put(id, state);
            }
        });
        return resources;
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

    private void sendSourceTrace(CommandSender sender, Player target, String attributeFilter) {
        AttributeSourceTraceReport report = attributeService.attributeTraceService().trace(target, attributeFilter);
        String filter = Texts.normalizeId(attributeFilter);
        if (Texts.isBlank(filter)) {
            messages().send(sender, "command.source.header", Map.of(
                    "player", MiniMessages.escape(target.getName())
            ));
        } else {
            messages().send(sender, "command.source.header_filtered", Map.of(
                    "player", MiniMessages.escape(target.getName()),
                    "attribute", MiniMessages.escape(filter)
            ));
        }
        int count = 0;
        for (AttributeContributionTrace trace : report.contributions()) {
            if (Texts.isNotBlank(filter) && !filter.equals(Texts.normalizeId(trace.attributeId()))) {
                continue;
            }
            messages().sendRaw(sender, formatContributionTrace(trace));
            count++;
        }
        if (count == 0) {
            messages().send(sender, "command.source.empty");
        }
    }

    private String formatContributionTrace(AttributeContributionTrace trace) {
        String value = Numbers.formatNumber(trace.value(), "0.##");
        String sign = trace.value() >= 0D ? "+" : "";
        String passed = trace.conditionPassed() ? "" : messages().message("command.source.condition_failed");
        return messages().message("command.source.line", Map.of(
                "value", MiniMessages.escape(sign + value),
                "attribute", MiniMessages.escape(trace.attributeId()),
                "source_type", MiniMessages.escape(trace.sourceType()),
                "source_label", MiniMessages.escape(trace.sourceLabel()),
                "condition", passed
        ));
    }

    private void sendDamageTrace(CommandSender sender, DamageTraceRecord record, boolean exportJson) {
        if (record == null) {
            messages().send(sender, "command.trace.empty");
            return;
        }
        if (exportJson) {
            messages().sendRaw(sender, Jsons.stringify(record.toMap()));
            return;
        }
        messages().sendRaw(sender, formatTraceSummary(record));
        for (var stage : record.stages()) {
            messages().sendRaw(sender, messages().message("command.trace.stage", Map.of(
                    "stage", MiniMessages.escape(stage.stageId()),
                    "input", Numbers.formatNumber(stage.input(), "0.##"),
                    "output", Numbers.formatNumber(stage.output(), "0.##")
            )));
        }
    }

    private String formatTraceSummary(DamageTraceRecord record) {
        if (record == null) {
            return messages().message("command.trace.none");
        }
        return messages().message("command.trace.summary", Map.of(
                "trace_id", record.traceId(),
                "attacker", MiniMessages.escape(record.attackerLabel()),
                "target", MiniMessages.escape(record.targetLabel()),
                "damage_type", MiniMessages.escape(record.damageTypeId()),
                "cause", MiniMessages.escape(record.cause()),
                "final_damage", Numbers.formatNumber(record.finalDamage(), "0.##"),
                "apply_mode", MiniMessages.escape(record.applyMode())
        ));
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
        for (Map.Entry<String, Double> entry : orderedDumpValues(snapshot)) {
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
