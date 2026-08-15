package emaki.jiuwu.craft.accessory.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.accessory.EmakiAccessoryPlugin;
import emaki.jiuwu.craft.accessory.model.AccessorySlot;
import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class AccessoryCommandRouter {

    private static final String PERMISSION_ROOT = "emakiaccessory";
    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";

    public static final String PERMISSION_EDIT_OTHERS = PERMISSION_ROOT + ".admin.edit";

    private static final List<String> SUBCOMMANDS =
            List.of("help", "open", "list", "reload", "debug", "admin");
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("view", "edit", "clear", "save");

    private final EmakiAccessoryPlugin plugin;

    public AccessoryCommandRouter(EmakiAccessoryPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "open" -> handleOpen(sender);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender, args);
            case "admin" -> handleAdmin(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    result.add(sub);
                }
            }
            return result;
        }
        String head = args[0].toLowerCase(Locale.ROOT);
        if ("debug".equals(head)) {
            return plugin.debugCommand() == null
                    ? List.of()
                    : plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        if ("admin".equals(head)) {
            if (args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (String sub : ADMIN_SUBCOMMANDS) {
                    if (sub.startsWith(prefix)) {
                        result.add(sub);
                    }
                }
                return result;
            }
            if (args.length == 3) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        result.add(online.getName());
                    }
                }
            }
        }
        return result;
    }

    private boolean handleOpen(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_USE)) {
            message(sender, "general.no_permission");
            return true;
        }
        plugin.executionDispatcher().runEntity(plugin, player, () -> plugin.openOwn(player), () -> {

        });
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_USE)) {
            message(sender, "general.no_permission");
            return true;
        }
        PlayerAccessories accessories = plugin.accessoryStore().cached(player.getUniqueId());
        if (accessories == null) {
            message(sender, "general.data_loading");
            return true;
        }
        message(sender, "command.list_header", Map.of(
                "used", String.valueOf(accessories.occupiedCount()),
                "max", String.valueOf(plugin.partRegistry().slotCount())));
        for (String slotInstanceId : plugin.partRegistry().slotInstanceIds()) {
            AccessorySlot slot = plugin.partRegistry().slot(slotInstanceId);
            boolean occupied = accessories.itemAt(slotInstanceId) != null;
            message(sender, occupied ? "command.list_entry_filled" : "command.list_entry_empty", Map.of(
                    "slot", slotInstanceId,
                    "part", slot == null ? slotInstanceId : slot.partId()));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            message(sender, "general.no_permission");
            return true;
        }
        long startTime = System.currentTimeMillis();
        int slots = plugin.reloadContent();
        long elapsedMs = System.currentTimeMillis() - startTime;
        message(sender, "command.reload_done", Map.of(
                "slots", String.valueOf(slots),
                "sets", String.valueOf(plugin.setService().definitions().size()),
                "issues", String.valueOf(plugin.partLoader().issues().size()
                        + plugin.setLoader().issues().size()
                        + plugin.accessoryGuiService().issues().size())));
        plugin.messageService().sendRaw(sender, "<gray>重载耗时: <white>" + elapsedMs + "ms</white></gray>");
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DEBUG)) {
            message(sender, "general.no_permission");
            return true;
        }
        if (plugin.debugCommand() == null) {
            message(sender, "command.debug_unavailable");
            return true;
        }
        plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length),
                plugin.messageService());
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            message(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2) {
            message(sender, "command.admin_usage");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "view" -> handleAdminOpen(sender, args, false);
            case "edit" -> handleAdminOpen(sender, args, true);
            case "clear" -> handleAdminClear(sender, args);
            case "save" -> handleAdminSave(sender);
            default -> {
                message(sender, "command.admin_usage");
                yield true;
            }
        };
    }

    private boolean handleAdminOpen(CommandSender sender, String[] args, boolean writable) {
        if (!(sender instanceof Player viewer)) {
            message(sender, "general.players_only");
            return true;
        }
        if (writable && !viewer.hasPermission(PERMISSION_EDIT_OTHERS)) {
            message(sender, "general.no_permission");
            return true;
        }
        if (args.length < 3) {
            message(sender, "command.admin_usage");
            return true;
        }
        UUID targetId = resolveTarget(args[2]);
        if (targetId == null) {
            message(sender, "command.unknown_player", Map.of("player", args[2]));
            return true;
        }
        PlayerAccessories cached = plugin.accessoryStore().cached(targetId);
        if (cached != null) {
            openFor(viewer, cached, writable);
            return true;
        }

        if (writable) {
            message(sender, "command.admin_target_offline");
            return true;
        }
        plugin.accessoryStore().beginSessionAsync(targetId, args[2]).thenAccept(accessories -> {
            if (accessories == null) {
                message(sender, "command.admin_load_failed", Map.of("player", args[2]));
                return;
            }
            plugin.executionDispatcher().runEntity(plugin, viewer,
                    () -> openFor(viewer, accessories, false), () -> {

                    });
        });
        return true;
    }

    private void openFor(Player viewer, PlayerAccessories accessories, boolean writable) {
        plugin.executionDispatcher().runEntity(plugin, viewer, () -> {
            if (writable) {
                UUID currentWriter = plugin.writeSessions().currentWriter(accessories.playerId());
                if (currentWriter != null && !currentWriter.equals(viewer.getUniqueId())) {
                    message(viewer, "command.admin_target_busy");
                    return;
                }
            } else {

                plugin.writeSessions().release(accessories.playerId(), viewer.getUniqueId());
            }
            if (!plugin.open(viewer, accessories)) {
                message(viewer, "command.open_failed");
            }
        }, () -> {

        });
    }

    private boolean handleAdminClear(CommandSender sender, String[] args) {
        if (args.length < 3) {
            message(sender, "command.admin_usage");
            return true;
        }
        UUID targetId = resolveTarget(args[2]);
        if (targetId == null) {
            message(sender, "command.unknown_player", Map.of("player", args[2]));
            return true;
        }
        if (plugin.accessoryStore().cached(targetId) == null) {
            message(sender, "command.admin_target_offline");
            return true;
        }
        UUID currentWriter = plugin.writeSessions().currentWriter(targetId);
        if (currentWriter != null) {
            message(sender, "command.admin_target_busy");
            return true;
        }
        int removed = plugin.adminService().clear(sender, targetId);
        if (removed < 0) {
            message(sender, "command.admin_clear_failed", Map.of("player", args[2]));
            return true;
        }
        PlayerAccessories accessories = plugin.accessoryStore().cached(targetId);
        Player target = Bukkit.getPlayer(targetId);
        if (accessories != null && target != null) {
            plugin.executionDispatcher().runEntity(plugin, target,
                    () -> plugin.refreshContributions(accessories), () -> {

                    });
        }
        plugin.accessoryStore().saveAsync(targetId);
        message(sender, "command.admin_clear_done", Map.of(
                "player", args[2], "count", String.valueOf(removed)));
        return true;
    }

    private boolean handleAdminSave(CommandSender sender) {
        plugin.accessoryStore().saveAllAsync().thenAccept(saved ->
                message(sender, "command.admin_save_done", Map.of("count", String.valueOf(saved))));
        return true;
    }

    private UUID resolveTarget(String name) {
        if (Texts.isBlank(name)) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        return offline == null ? null : offline.getUniqueId();
    }

    private void sendHelp(CommandSender sender) {
        message(sender, "command.help");
    }

    private void message(CommandSender sender, String key) {
        if (plugin.messageService() != null) {
            plugin.messageService().send(sender, key);
        }
    }

    private void message(CommandSender sender, String key, Map<String, ?> replacements) {
        if (plugin.messageService() != null) {
            plugin.messageService().send(sender, key, replacements);
        }
    }
}
