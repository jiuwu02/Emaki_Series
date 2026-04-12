package emaki.jiuwu.craft.attribute.command;

import java.util.ArrayList;
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

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.config.DamageCauseRule;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.MessageService;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

public final class AttributeCommand implements TabExecutor {

    private final EmakiAttributePlugin plugin;
    private final AttributeService attributeService;

    public AttributeCommand(EmakiAttributePlugin plugin, AttributeService attributeService) {
        this.plugin = plugin;
        this.attributeService = attributeService;
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
            for (String candidate : List.of("help", "reload", "resync", "preview", "dump", "debug", "lint")) {
                if (candidate.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(candidate);
                }
            }
            return result;
        }
        if (args.length == 2 && "resync".equalsIgnoreCase(args[0])) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(player.getName());
                }
            }
            result.add("all");
            return result;
        }
        if (args.length == 2 && "dump".equalsIgnoreCase(args[0])) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(player.getName());
                }
            }
            return result;
        }
        if (args.length == 2 && "preview".equalsIgnoreCase(args[0])) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(player.getName());
                }
            }
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
        if (args.length == 2 && "debug".equalsIgnoreCase(args[0])) {
            for (String candidate : List.of("toggle", "on", "off")) {
                if (candidate.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(candidate);
                }
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(player.getName());
                }
            }
            return result;
        }
        if (args.length == 3 && "debug".equalsIgnoreCase(args[0]) && Bukkit.getPlayerExact(args[1]) != null) {
            for (String candidate : List.of("toggle", "on", "off")) {
                if (candidate.startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    result.add(candidate);
                }
            }
            return result;
        }
        return result;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("emakiattribute.reload") && !sender.hasPermission("emakiattribute.admin")) {
            messages().send(sender, "command.reload.no_permission");
            return true;
        }
        messages().send(sender, "command.reload.started");
        plugin.reloadPluginStateAsync(true, message -> plugin.getServer().getScheduler().runTask(plugin, () -> messages().sendRaw(sender, message)))
                .thenRun(() -> messages().send(sender, "command.reload.success"))
                .thenRun(() -> messages().send(sender, "command.reload.summary", Map.of(
                        "attributes", attributeService.attributeRegistry().all().size(),
                        "damage_types", attributeService.damageTypeRegistry().all().size(),
                        "profiles", attributeService.defaultProfileRegistry().all().size()
                )))
                .exceptionally(throwable -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> messages().send(sender, "command.reload.failed", Map.of(
                            "error", rootCauseMessage(throwable)
                    )));
                    return null;
                });
        return true;
    }

    private boolean handleResync(CommandSender sender, String[] args) {
        if (!sender.hasPermission("emakiattribute.resync") && !sender.hasPermission("emakiattribute.admin")) {
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
        if (!sender.hasPermission("emakiattribute.debug") && !sender.hasPermission("emakiattribute.admin")) {
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
        messages().send(sender, "command.preview.name", Map.of("name", previewItemName(itemStack)));
        messages().send(sender, "command.preview.signature", Map.of(
                "signature", snapshot == null || Texts.isBlank(snapshot.sourceSignature()) ? "-" : snapshot.sourceSignature()
        ));
        messages().send(sender, "command.preview.values", Map.of(
                "values", formatPreviewValues(snapshot)
        ));
        return true;
    }

    private boolean handleDump(CommandSender sender, String[] args) {
        if (!sender.hasPermission("emakiattribute.debug") && !sender.hasPermission("emakiattribute.admin")) {
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
        AttributeSnapshot snapshot = attributeService.collectCombatSnapshot(target);
        messages().send(sender, "command.dump.player", Map.of("player", target.getName()));
        messages().sendComponent(sender, buildDumpSignatureComponent(snapshot));
        messages().sendComponent(sender, buildDumpValuesComponent(snapshot));
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
        if (!sender.hasPermission("emakiattribute.debug") && !sender.hasPermission("emakiattribute.admin")) {
            messages().send(sender, "command.debug.no_permission");
            return true;
        }
        Player target = null;
        String action = "toggle";
        if (args.length >= 2) {
            Player namedTarget = Bukkit.getPlayerExact(args[1]);
            if (namedTarget != null) {
                target = namedTarget;
                if (args.length >= 3) {
                    action = args[2];
                }
            } else if (sender instanceof Player player && isDebugAction(args[1])) {
                target = player;
                action = args[1];
            } else if (sender instanceof Player player && args.length == 2 && player.getName().equalsIgnoreCase(args[1])) {
                target = player;
            } else {
                messages().send(sender, sender instanceof Player ? "command.debug.player_not_found" : "command.debug.console_usage", Map.of("player", args[1]));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            messages().send(sender, "command.debug.console_usage");
            return true;
        }
        if (target == null) {
            messages().send(sender, "command.debug.console_usage");
            return true;
        }
        boolean enabled;
        if ("on".equalsIgnoreCase(action) || "enable".equalsIgnoreCase(action) || "true".equalsIgnoreCase(action)) {
            enabled = attributeService.setCombatDebug(target, true);
        } else if ("off".equalsIgnoreCase(action) || "disable".equalsIgnoreCase(action) || "false".equalsIgnoreCase(action)) {
            enabled = attributeService.setCombatDebug(target, false);
        } else {
            enabled = attributeService.toggleCombatDebug(target);
        }
        messages().send(sender, enabled ? "command.debug.enabled" : "command.debug.disabled", Map.of("player", target.getName()));
        return true;
    }

    private boolean handleLint(CommandSender sender) {
        if (!sender.hasPermission("emakiattribute.reload") && !sender.hasPermission("emakiattribute.admin")) {
            messages().send(sender, "command.lint.no_permission");
            return true;
        }
        messages().sendRaw(sender, messages().message("command.lint.header"));
        reportLintSection(sender, "配置", collectConfigIssues());
        reportLintSection(sender, "属性定义", plugin.attributeRegistry().issues());
        reportLintSection(sender, "伤害类型", plugin.damageTypeRegistry().issues());
        reportLintSection(sender, "默认组", plugin.defaultProfileRegistry().issues());
        reportLintSection(sender, "Lore 格式", plugin.loreFormatRegistry().issues());
        reportLintSection(sender, "属性预设", plugin.presetRegistry().issues());
        reportLintSection(sender, "PDC 读取规则", plugin.pdcReadRuleLoader().issues());
        messages().sendRaw(sender, messages().message("command.lint.footer"));
        return true;
    }

    private Component buildDumpSignatureComponent(AttributeSnapshot snapshot) {
        Component prefix = messages().render(messages().message("general.prefix"));
        Component label = messages().render(messages().message("command.dump.signature"));
        Component hover = Component.text(snapshot == null || snapshot.sourceSignature() == null || snapshot.sourceSignature().isBlank()
                ? "没有签名"
                : snapshot.sourceSignature(), NamedTextColor.YELLOW);
        return prefix.append(Component.space()).append(label.hoverEvent(HoverEvent.showText(hover)));
    }

    private Component buildDumpValuesComponent(AttributeSnapshot snapshot) {
        Component prefix = messages().render(messages().message("general.prefix"));
        Component label = messages().render(messages().message("command.dump.values"));
        Component hover = buildDumpValuesHover(snapshot);
        return prefix.append(Component.space()).append(label.hoverEvent(HoverEvent.showText(hover)));
    }

    private Component buildDumpValuesHover(AttributeSnapshot snapshot) {
        List<Component> lines = new ArrayList<>();
        for (Map.Entry<String, Double> entry : orderedDumpValues(snapshot)) {
            String attributeId = entry.getKey();
            Double value = entry.getValue();
            if (attributeId == null || value == null || Double.compare(value, 0D) == 0) {
                continue;
            }
            var definition = attributeService.attributeRegistry().resolve(attributeId);
            String displayName = definition == null ? attributeId : definition.displayName();
            String formattedValue = Numbers.formatNumber(value, "0.##");
            lines.add(Component.text()
                    .append(Component.text(displayName, NamedTextColor.AQUA))
                    .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                    .append(Component.text(attributeId, NamedTextColor.WHITE))
                    .append(Component.text("): ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(formattedValue, NamedTextColor.YELLOW))
                    .build());
        }
        if (lines.isEmpty()) {
            return Component.text("没有非零属性", NamedTextColor.GRAY);
        }
        Component hover = Component.empty();
        boolean first = true;
        for (Component line : lines) {
            if (!first) {
                hover = hover.append(Component.newline());
            }
            hover = hover.append(line);
            first = false;
        }
        return hover;
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
        Map<String, ResourceState> resources = new java.util.LinkedHashMap<>();
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
        messages().send(sender, "command.help.lint");
        messages().sendRaw(sender, messages().message("command.help.footer"));
    }

    private MessageService messages() {
        return plugin.messageService();
    }

    private boolean isDebugAction(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return "toggle".equals(normalized)
                || "on".equals(normalized)
                || "off".equals(normalized)
                || "enable".equals(normalized)
                || "disable".equals(normalized)
                || "true".equals(normalized)
                || "false".equals(normalized);
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        if (current == null || current.getMessage() == null || current.getMessage().isBlank()) {
            return "unknown";
        }
        return current.getMessage();
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

    private String previewItemName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "-";
        }
        if (itemStack.hasItemMeta() && itemStack.getItemMeta() != null && itemStack.getItemMeta().hasDisplayName()) {
            return itemStack.getItemMeta().getDisplayName();
        }
        return itemStack.getType().name();
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
