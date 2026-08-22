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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.AttributePermissions;
import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeContributionTrace;
import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.AttributeSourceTraceReport;
import emaki.jiuwu.craft.attribute.model.DamageTraceRecord;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.util.Jsons;

final class AttributeDiagnosticsCommand {

    private final EmakiAttributePlugin plugin;
    private final AttributeService attributeService;

    AttributeDiagnosticsCommand(EmakiAttributePlugin plugin, AttributeService attributeService) {
        this.plugin = plugin;
        this.attributeService = attributeService;
    }

    private MessageService messages() {
        return plugin.messageService();
    }

    boolean handleDump(CommandSender sender, String[] args) {
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

    boolean handleDebug(CommandSender sender, String[] args) {
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

    boolean handleSource(CommandSender sender, String[] args) {
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

    boolean handleTrace(CommandSender sender, String[] args) {
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

    private boolean supportsHover(CommandSender sender) {
        return sender instanceof Player;
    }

    private String displayNameOf(String attributeId) {
        var definition = attributeService.attributeRegistry().resolve(attributeId);
        return definition == null ? attributeId : definition.displayName();
    }

    List<Map.Entry<String, Double>> orderedDumpValues(AttributeSnapshot snapshot) {
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
}
