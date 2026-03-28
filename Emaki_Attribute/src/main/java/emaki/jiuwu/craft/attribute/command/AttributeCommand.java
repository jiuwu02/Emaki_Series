package emaki.jiuwu.craft.attribute.command;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.MessageService;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

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
            case "reload" -> handleReload(sender);
            case "resync" -> handleResync(sender, args);
            case "preview" -> handlePreview(sender, args);
            case "dump" -> handleDump(sender, args);
            case "lint" -> handleLint(sender);
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
            for (String candidate : List.of("help", "reload", "resync", "preview", "dump", "lint")) {
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
        if (args.length == 2 && "preview".equalsIgnoreCase(args[0])) {
            for (String candidate : List.of("hand", "offhand", "helmet", "chestplate", "leggings", "boots")) {
                if (candidate.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(candidate);
                }
            }
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
        return result;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("emakiattribute.reload") && !sender.hasPermission("emakiattribute.admin")) {
            messages().send(sender, "command.reload.no_permission");
            return true;
        }
        plugin.reloadPluginState(true);
        messages().send(sender, "command.reload.success");
        messages().send(sender, "command.reload.summary", Map.of(
            "attributes", attributeService.attributeRegistry().all().size(),
            "damage_types", attributeService.damageTypeRegistry().all().size(),
            "profiles", attributeService.defaultProfileRegistry().all().size()
        ));
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
        Player player;
        String slot;
        if (sender instanceof Player self) {
            player = self;
            slot = args.length >= 2 ? args[1] : "hand";
        } else {
            if (args.length < 3) {
                messages().send(sender, "command.preview.console_usage");
                return true;
            }
            player = Bukkit.getPlayerExact(args[1]);
            if (player == null) {
                messages().send(sender, "command.preview.player_not_found", Map.of("player", args[1]));
                return true;
            }
            slot = args[2];
        }
        ItemStack itemStack = resolveItem(player, slot);
        if (itemStack == null || itemStack.getType().isAir()) {
            messages().send(sender, "command.preview.no_item");
            return true;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        AttributeSnapshot snapshot = itemMeta != null && itemMeta.hasLore()
            ? attributeService.loreParser().parse(itemMeta.lore()).snapshot()
            : AttributeSnapshot.empty("");
        messages().send(sender, "command.preview.item", Map.of("item", itemStack.getType().name()));
        if (itemMeta != null && itemMeta.hasCustomName()) {
            messages().send(sender, "command.preview.name", Map.of("name", MiniMessages.serialize(itemMeta.customName())));
        }
        messages().send(sender, "command.preview.signature", Map.of("signature", snapshot.sourceSignature()));
        messages().send(sender, "command.preview.values", Map.of("values", snapshot.values()));
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
        messages().send(sender, "command.dump.signature", Map.of("signature", snapshot.sourceSignature()));
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

    private boolean handleLint(CommandSender sender) {
        if (!sender.hasPermission("emakiattribute.debug") && !sender.hasPermission("emakiattribute.admin")) {
            messages().send(sender, "command.lint.no_permission");
            return true;
        }
        messages().send(sender, "command.lint.config_status", Map.of("config", plugin.configModel()));
        reportIssues(sender, messages().message("label.attribute"), attributeService.attributeRegistry().issues());
        reportIssues(sender, messages().message("label.damage_type"), attributeService.damageTypeRegistry().issues());
        reportIssues(sender, messages().message("label.default_profile"), attributeService.defaultProfileRegistry().issues());
        reportIssues(sender, messages().message("label.lore_format"), attributeService.loreFormatRegistry().issues());
        reportIssues(sender, messages().message("label.preset"), attributeService.presetRegistry().issues());
        return true;
    }

    private void reportIssues(CommandSender sender, String name, List<String> issues) {
        if (issues.isEmpty()) {
            messages().send(sender, "command.lint.ok", Map.of("name", name));
            return;
        }
        messages().send(sender, "command.lint.issues", Map.of("name", name, "count", issues.size()));
        for (String issue : issues) {
            messages().sendRaw(sender, "<gray>- " + issue + "</gray>");
        }
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

    private ItemStack resolveItem(Player player, String slot) {
        PlayerInventory inventory = player.getInventory();
        if (slot == null) {
            return inventory.getItemInMainHand();
        }
        return switch (slot.toLowerCase(Locale.ROOT)) {
            case "hand", "main", "mainhand" -> inventory.getItemInMainHand();
            case "off", "offhand" -> inventory.getItemInOffHand();
            case "head", "helmet" -> inventory.getHelmet();
            case "chest", "chestplate" -> inventory.getChestplate();
            case "legs", "leggings" -> inventory.getLeggings();
            case "boots" -> inventory.getBoots();
            default -> inventory.getItemInMainHand();
        };
    }

    private void sendHelp(CommandSender sender) {
        messages().send(sender, "command.help.reload");
        messages().send(sender, "command.help.resync");
        messages().send(sender, "command.help.preview");
        messages().send(sender, "command.help.dump");
        messages().send(sender, "command.help.lint");
    }

    private MessageService messages() {
        return plugin.messageService();
    }
}
