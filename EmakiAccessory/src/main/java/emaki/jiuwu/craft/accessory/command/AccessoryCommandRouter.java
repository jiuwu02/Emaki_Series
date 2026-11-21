package emaki.jiuwu.craft.accessory.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.accessory.EmakiAccessoryPlugin;
import emaki.jiuwu.craft.accessory.model.AccessoryPage;
import emaki.jiuwu.craft.accessory.model.AccessorySlot;
import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.accessory.service.AccessoryPageRegistry;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.command.CommandKeys;
import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;

public final class AccessoryCommandRouter {

    private static final String PERMISSION_ROOT = "emakiaccessory";
    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";
    private static final String PERMISSION_EDIT = PERMISSION_ROOT + ".edit";

    public static final String PERMISSION_EDIT_OTHERS = PERMISSION_ROOT + ".admin.edit";

    private static final List<String> SUBCOMMANDS =
            List.of("help", "open", "list", "pages", "retrieve", "edit", "reload", "debug", "admin");
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("view", "edit", "clear", "save");

    private final EmakiAccessoryPlugin plugin;

    public AccessoryCommandRouter(EmakiAccessoryPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelpPage(sender, helpRoot(label), Arrays.asList(args).subList(1, args.length));
                yield true;
            }
            case "open" -> handleOpen(sender, args);
            case "list" -> handleList(sender);
            case "pages" -> handlePages(sender);
            case "retrieve" -> handleRetrieve(sender, args);
            case "edit" -> handleEdit(sender, args);
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender, args);
            case "admin" -> handleAdmin(sender, args);
            default -> {
                sendHelp(sender, label);
                yield true;
            }
        };
    }

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length <= 1) {
            return CommandTabHelper.completeSubcommands(SUBCOMMANDS,
                    args.length == 0 ? "" : args[0]);
        }
        String head = args[0].toLowerCase(Locale.ROOT);
        if (("open".equals(head) || "retrieve".equals(head)) && args.length == 2) {
            String prefix = Texts.lower(args[1]);
            for (String pageId : plugin.pageRegistry().pageIds()) {
                if (pageId.startsWith(prefix)) {
                    result.add(pageId);
                }
            }
            return result;
        }
        if ("edit".equals(head) && args.length == 2) {
            String prefix = Texts.lower(args[1]);
            if (plugin.partLoader() != null && plugin.partLoader().file().isFile()
                    && "parts".startsWith(prefix)) {
                result.add("parts");
            }
            if (plugin.editorModule() != null) {
                for (String id : plugin.editorModule().completeEditIds()) {
                    if (id.startsWith(prefix)) {
                        result.add(id);
                    }
                }
            }
            return result;
        }
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
            if (args.length == 3 && !"save".equals(Texts.lower(args[1]))) {
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

    private boolean handleOpen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_USE)) {
            message(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        String requested = args.length < 2 ? "" : args[1];
        plugin.executionDispatcher().runEntity(plugin, player,
                () -> plugin.openOwn(player, requested), () -> {

                });
        return true;
    }

    private boolean handlePages(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_USE)) {
            message(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        PlayerAccessories accessories = plugin.accessoryStore().cached(player.getUniqueId());
        if (accessories == null) {
            message(sender, "general.data_loading");
            return true;
        }
        AccessoryPageRegistry pages = plugin.pageRegistry();
        message(sender, "command.pages_header", Map.of("count", String.valueOf(pages.pageIds().size())));
        String enabled = Texts.normalizeId(accessories.enabledPage());
        for (String pageId : pages.pageIds()) {
            AccessoryPage page = pages.page(pageId);
            String key = pageId.equals(enabled)
                    ? "command.pages_entry_enabled"
                    : plugin.canUsePage(player, pageId)
                            ? "command.pages_entry"
                            : "command.pages_entry_locked";
            message(sender, key, Map.of(
                    "page", pageId,
                    "name", page == null ? pageId : page.label(),
                    "count", String.valueOf(accessories.occupiedCount(pageId))));
        }
        return true;
    }

    private boolean handleRetrieve(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_USE)) {
            message(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (args.length < 2) {
            message(sender, "command.retrieve_usage");
            return true;
        }
        String pageId = Texts.normalizeId(args[1]);
        if (!plugin.pageRegistry().hasPage(pageId)) {
            message(sender, "command.retrieve_unknown_page", Map.of("page", args[1]));
            return true;
        }
        PlayerAccessories accessories = plugin.accessoryStore().cached(player.getUniqueId());
        if (accessories == null) {
            message(sender, "general.data_loading");
            return true;
        }
        UUID playerId = player.getUniqueId();
        if (!plugin.writeSessions().acquire(playerId, playerId)) {
            message(sender, "command.retrieve_busy");
            return true;
        }
        plugin.executionDispatcher().runEntity(plugin, player, () -> {
            try {
                int retrieved = plugin.retrievePage(player, playerId, pageId);
                if (retrieved == 0) {
                    message(player, "command.retrieve_empty", Map.of("page", pageId));
                    return;
                }
                message(player, "command.retrieve_done", Map.of(
                        "page", pageId,
                        "count", String.valueOf(retrieved)));
            } finally {
                plugin.writeSessions().release(playerId, playerId);
            }
        }, () -> plugin.writeSessions().release(playerId, playerId));
        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_EDIT)) {
            message(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (args.length >= 2 && !Texts.isBlank(args[1])) {
            String id = Texts.normalizeId(args[1]);
            plugin.executionDispatcher().runEntity(plugin, player, () -> {
                if (plugin.editorModule() == null
                        || !plugin.editorModule().openById(player, id)) {
                    message(player, "editor.open_failed", Map.of("id", id));
                }
            }, () -> {

            });
            return true;
        }
        plugin.executionDispatcher().runEntity(plugin, player, () -> {
            if (plugin.editorService() != null) {
                plugin.editorService().openBrowser(player);
            }
        }, () -> {

        });
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_USE)) {
            message(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        PlayerAccessories accessories = plugin.accessoryStore().cached(player.getUniqueId());
        if (accessories == null) {
            message(sender, "general.data_loading");
            return true;
        }
        String pageId = plugin.pageRegistry().resolveEnabledPage(accessories.enabledPage());
        List<String> slots = plugin.pageRegistry().slotsOf(pageId);
        message(sender, "command.list_header", Map.of(
                "page", pageId,
                "used", String.valueOf(accessories.occupiedCount(pageId)),
                "max", String.valueOf(slots.size())));
        for (String slotInstanceId : slots) {
            AccessorySlot slot = plugin.partRegistry().slot(slotInstanceId);
            boolean occupied = accessories.itemAt(pageId, slotInstanceId) != null;
            message(sender, occupied ? "command.list_entry_filled" : "command.list_entry_empty", Map.of(
                    "slot", slotInstanceId,
                    "part", slot == null ? slotInstanceId : slot.partId()));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            message(sender, CommandKeys.NO_PERMISSION);
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
        plugin.messageService().send(sender, "command.reload_elapsed", Map.of("elapsed_ms", String.valueOf(elapsedMs)));
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DEBUG)) {
            message(sender, CommandKeys.NO_PERMISSION);
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
            message(sender, CommandKeys.NO_PERMISSION);
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
            message(sender, CommandKeys.NO_PERMISSION);
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
        if (plugin.accessoryStore().cached(targetId) != null) {
            openFor(viewer, targetId, writable);
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
                    () -> openFor(viewer, targetId, false), () -> {

                    });
        });
        return true;
    }

    private void openFor(Player viewer, UUID targetId, boolean writable) {
        plugin.executionDispatcher().runEntity(plugin, viewer, () -> {
            if (writable) {
                UUID currentWriter = plugin.writeSessions().currentWriter(targetId);
                if (currentWriter != null && !currentWriter.equals(viewer.getUniqueId())) {
                    message(viewer, "command.admin_target_busy");
                    return;
                }
            } else {

                plugin.writeSessions().release(targetId, viewer.getUniqueId());
            }
            if (!plugin.open(viewer, targetId, "")) {
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
        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            plugin.executionDispatcher().runEntity(plugin, target,
                    () -> plugin.refreshContributions(targetId), () -> {

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

    private void sendHelp(CommandSender sender, String label) {
        sendHelpPage(sender, helpRoot(label), List.of());
    }

    private String helpRoot(String label) {
        return "/" + (label == null || label.isBlank() ? "emakiaccessory" : label);
    }

    private void sendHelpPage(CommandSender sender, String root, List<String> path) {
        if (path.isEmpty()) {
            sendHelpLines(sender, root, "command.help_header",
                    "command.help_entry_help", "command.help_entry_open", "command.help_entry_list",
                    "command.help_entry_pages", "command.help_entry_retrieve", "command.help_entry_edit",
                    "command.help_entry_reload", "command.help_entry_debug", "command.help_entry_admin");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(0).toLowerCase(Locale.ROOT)) {
            case "retrieve" -> sendIdHelp(sender, root, "retrieve",
                    "command.help_retrieve_header", "command.help_value_desc_retrieve",
                    new ArrayList<>(plugin.pageRegistry().pageIds()));
            case "admin" -> sendAdminHelp(sender, root, path);
            case "debug" -> sendDebugHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of());
        }
    }

    private void sendIdHelp(CommandSender sender, String root, String command,
            String headerKey, String descKey, List<String> ids) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message(headerKey, Map.of("root", root)));
        List<String> values = new ArrayList<>(ids);
        values.sort(String::compareTo);
        if (values.isEmpty()) {
            sendHelpLine(sender, messageService.message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String value : values) {
            sendValueEntry(sender, value,
                    messageService.message(descKey, Map.of("value", value)),
                    root + " " + command + " " + value);
        }
        sendHelpFooter(sender);
    }

    private void sendAdminHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 2) {
            String tail = path.get(1).toLowerCase(Locale.ROOT);
            if ("view".equals(tail)) {
                sendPlayerHelp(sender, root, "admin view",
                        "command.help_admin_view_header", "command.help_value_desc_admin_view");
                return;
            }
            if ("edit".equals(tail)) {
                sendPlayerHelp(sender, root, "admin edit",
                        "command.help_admin_edit_header", "command.help_value_desc_admin_edit");
                return;
            }
            if ("clear".equals(tail)) {
                sendPlayerHelp(sender, root, "admin clear",
                        "command.help_admin_clear_header", "command.help_value_desc_admin_clear");
                return;
            }
        }
        sendHelpLines(sender, root, "command.help_admin_header",
                "command.help_admin_entry_view", "command.help_admin_entry_edit",
                "command.help_admin_entry_clear", "command.help_admin_entry_save");
        sendHelpFooter(sender);
    }

    private void sendDebugHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() < 2) {
            sendHelpLines(sender, root, "command.help_debug_header",
                    "command.help_debug_entry_status", "command.help_debug_entry_on",
                    "command.help_debug_entry_off", "command.help_debug_entry_player",
                    "command.help_debug_entry_module", "command.help_debug_entry_action");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(1).toLowerCase(Locale.ROOT)) {
            case "player" -> sendPlayerHelp(sender, root, "debug player",
                    "command.help_debug_player_header", "command.help_value_desc_debug_player");
            case "module" -> sendModuleHelp(sender, root);
            case "action" -> sendDebugActionHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of("debug"));
        }
    }

    private void sendPlayerHelp(CommandSender sender, String root, String command, String headerKey, String descKey) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message(headerKey, Map.of("root", root)));
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName));
        if (players.isEmpty()) {
            sendHelpLine(sender, messageService.message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (Player player : players) {
            String name = player.getName();
            sendValueEntry(sender, name,
                    messageService.message(descKey, Map.of("value", name)),
                    root + " " + command + " " + name);
        }
        sendHelpFooter(sender);
    }

    private void sendModuleHelp(CommandSender sender, String root) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message("command.help_debug_module_header", Map.of("root", root)));
        List<String> modules = plugin.debugCommand() == null
                ? List.of()
                : plugin.debugCommand().tabComplete(new String[] {"module", ""});
        if (modules.isEmpty()) {
            sendHelpLine(sender, messageService.message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String module : modules) {
            sendValueEntry(sender, module,
                    messageService.message("command.help_value_desc_debug_module", Map.of("value", module)),
                    root + " debug module " + module);
        }
        sendHelpFooter(sender);
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

    private void sendValueEntry(CommandSender sender, String value, String desc, String run) {
        sendHelpLine(sender, plugin.messageService().message("command.help_value_entry", Map.of(
                "value", value,
                "desc", desc,
                "run", run)));
    }

    private void sendHelpLines(CommandSender sender, String root, String... keys) {
        var messageService = plugin.messageService();
        Map<String, ?> replacements = Map.of("root", root);
        for (String key : keys) {
            sendHelpLine(sender, messageService.message(key, replacements));
        }
    }

    private void sendHelpLine(CommandSender sender, String line) {
        var messageService = plugin.messageService();
        messageService.sendComponent(sender, messageService.render(line));
    }

    private void sendHelpFooter(CommandSender sender) {
        sendHelpLine(sender, plugin.messageService().message("command.help_footer"));
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
