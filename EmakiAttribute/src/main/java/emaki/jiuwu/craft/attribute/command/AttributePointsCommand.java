package emaki.jiuwu.craft.attribute.command;

import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.AttributePermissions;
import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.ParentAttributeData;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.ParentAttributeService;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.Texts;

final class AttributePointsCommand {

    private final EmakiAttributePlugin plugin;
    private final AttributeService attributeService;

    AttributePointsCommand(EmakiAttributePlugin plugin, AttributeService attributeService) {
        this.plugin = plugin;
        this.attributeService = attributeService;
    }

    private MessageService messages() {
        return plugin.messageService();
    }

    boolean handlePoints(CommandSender sender, String[] args) {
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
}
