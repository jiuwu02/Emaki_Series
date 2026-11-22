package emaki.jiuwu.craft.gem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.gem.service.GemGuiMode;
import emaki.jiuwu.craft.gem.service.GemRerollSessionService;
import emaki.jiuwu.craft.corelib.api.command.CommandKeys;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport.Guard;

final class GemCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakigem";
    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";
    private static final String PERMISSION_EDIT = PERMISSION_ROOT + ".edit";

    private final EmakiGemPlugin plugin;

    GemCommandRouter(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label, List.of());
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelp(sender, label, Arrays.asList(args).subList(1, args.length));
                yield true;
            }
            case "gui" -> handleGuiCommand(sender, args);
            case "reload" -> handleReload(sender);
            case "edit" -> handleEdit(sender, args);
            case "inspect" -> handleInspect(sender, args);
            case "reroll" -> handleReroll(sender, args);
            case "clearstate" -> handleClearState(sender);
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
            List<String> subcommands = new ArrayList<>(List.of(
                    "help", "gui", "reload", "edit", "inspect", "reroll", "clearstate", "debug"));
            subcommands.removeIf(sub -> !hasSubcommandPermission(sender, sub));
            return CommandTabHelper.completeSubcommands(subcommands, args[0]);
        }
        if (args.length >= 2 && "debug".equalsIgnoreCase(args[0])) {
            if (!hasSubcommandPermission(sender, "debug")) {
                return result;
            }
            return plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 2) {
            if (!hasSubcommandPermission(sender, args[0].toLowerCase(Locale.ROOT))) {
                return result;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "gui" -> {
                    for (String sub : List.of("inlay", "upgrade", "extract", "reroll", "recalibrate", "open")) {
                        if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                            result.add(sub);
                        }
                    }
                }
                case "inspect" -> result.addAll(CommandTabHelper.completeOnlinePlayers(args[1]));
                case "edit" -> {
                    if (plugin.editorModule() != null) {
                        String prefix = args[1].toLowerCase(Locale.ROOT);
                        for (String id : plugin.editorModule().knownIds()) {
                            if (id.startsWith(prefix)) {
                                result.add(id);
                            }
                        }
                    }
                }
                case "reroll" -> {
                    for (String sub : List.of("full", "value", "confirm", "cancel", "status")) {
                        if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                            result.add(sub);
                        }
                    }
                }
                default -> {
                }
            }
            return result;
        }
        return result;
    }

    private boolean hasSubcommandPermission(CommandSender sender, String subcommand) {
        return switch (subcommand) {
            case "gui", "inspect", "reroll" -> sender.hasPermission(PERMISSION_USE)
                    || sender.hasPermission(PERMISSION_ADMIN);
            case "reload" -> sender.hasPermission(PERMISSION_RELOAD)
                    || sender.hasPermission(PERMISSION_ADMIN);
            case "edit" -> sender.hasPermission(PERMISSION_EDIT);
            case "clearstate" -> sender.hasPermission(PERMISSION_ADMIN);
            case "debug" -> sender.hasPermission(PERMISSION_DEBUG)
                    || sender.hasPermission(PERMISSION_ADMIN);
            default -> true;
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.messageService().send(sender, "general.reloading");
        long startTime = System.currentTimeMillis();
        plugin.reloadPluginStateAsync(true).thenRun(() -> runForSender(sender, () -> {
            long elapsedMs = System.currentTimeMillis() - startTime;
            plugin.messageService().send(sender, "general.reload_success");
            plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of(
                    "gems", plugin.gemLoader().all().size(),
                    "items", plugin.gemItemLoader().all().size(),
                    "guis", plugin.guiTemplateLoader().all().size()
            )));
            plugin.messageService().sendRaw(sender, "<gray>重载耗时: <white>" + elapsedMs + "ms</white></gray>");
        }));
        return true;
    }

    private void runForSender(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            plugin.scheduling().runForEntity(plugin, player, task, null);
            return;
        }
        plugin.scheduling().runGlobal(plugin, task);
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        EditorCommandSupport.Guard editGuard = EditorCommandSupport.check(
                sender, () -> plugin.editorService() != null && plugin.editorModule() != null, PERMISSION_EDIT);
        if (editGuard == Guard.DENIED) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (editGuard == Guard.NON_PLAYER) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (editGuard == Guard.UNAVAILABLE) {
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            plugin.editorService().openBrowser(player);
            return true;
        }
        if (plugin.editorModule().openById(player, args[1]) == null) {
            plugin.messageService().send(sender, "editor.open_failed",
                    Map.of("id", Texts.normalizeId(args[1])));
        }
        return true;
    }

    private boolean handleGui(CommandSender sender, GemGuiMode mode) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (!plugin.gemGuiService().open(player, mode)) {
            plugin.messageService().send(sender, "gui.open_failed");
        }
        return true;
    }

    private boolean handleGuiCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "inlay" -> handleGui(sender, GemGuiMode.INLAY);
            case "upgrade" -> handleGui(sender, GemGuiMode.UPGRADE);
            case "extract" -> handleGui(sender, GemGuiMode.EXTRACT);
            case "reroll" -> handleGui(sender, GemGuiMode.REROLL_FULL);
            case "recalibrate" -> handleGui(sender, GemGuiMode.REROLL_VALUE);
            case "open" -> handleGui(sender, GemGuiMode.OPEN_SOCKET);
            default -> {
                plugin.messageService().send(sender, "general.invalid_args");
                yield true;
            }
        };
    }

    private boolean handleInspect(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        Player player = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player self ? self : null);
        if (player == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(itemStack);
        GemState state = plugin.stateService().resolveState(itemStack);
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.header", Map.of("player", player.getName())));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "item_definition",
                "value", itemDefinition == null ? "-" : itemDefinition.id()
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "identified_source",
                "value", plugin.itemMatcher().identifyItem(itemStack) == null ? "-" : ItemSourceUtil.toShorthand(plugin.itemMatcher().identifyItem(itemStack))
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "opened_slots",
                "value", state == null ? "-" : state.openedSlotIndexes()
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "socket_assignments",
                "value", state == null ? "-" : state.socketAssignments().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue().token())
                        .toList()
        )));
        return true;
    }

    private boolean handleReroll(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "gem.reroll.usage");
            return true;
        }
        GemRerollSessionService service = plugin.rerollSessionService();
        if (service == null) {
            plugin.messageService().send(sender, "gem.reroll.unavailable");
            return true;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "full", "value" -> {
                GemRerollSessionService.OperationType type = "full".equalsIgnoreCase(args[1])
                        ? GemRerollSessionService.OperationType.FULL
                        : GemRerollSessionService.OperationType.VALUE;
                GemRerollSessionService.OpenResult result = service.open(player, type);
                if (!result.success()) {
                    sendRerollError(sender, result.errorKey());
                    return true;
                }
                plugin.messageService().send(sender, "gem.reroll.opened", Map.of(
                        "operation_type", operationTypeText(result.session().operationType())
                ));
            }
            case "confirm" -> {
                GemRerollSessionService.ActionResult result = service.confirm(player);
                if (result.success()) {
                    plugin.messageService().send(sender, "gui.gem.reroll_confirmed", Map.of(
                            "operation_id", result.session() == null ? "-" : result.session().operationId()
                    ));
                    return true;
                }
                sendRerollError(sender, result.errorKey());
            }
            case "cancel" -> plugin.messageService().send(sender,
                    service.abandon(player.getUniqueId()) ? "gem.reroll.cancelled" : "gem.reroll.session_missing");
            case "status" -> service.view(player.getUniqueId()).ifPresentOrElse(session ->
                    plugin.messageService().send(sender, "gem.reroll.status", Map.of(
                            "operation_type", session.operationType(),
                            "original_count", session.originalAffixes().size(),
                            "candidate_count", session.candidateAffixes().size(),
                            "expires_in", Math.max(0L,
                                    (session.expiryAt() - System.currentTimeMillis()) / 1000L)
                    )),
                    () -> plugin.messageService().send(sender, "gem.reroll.session_missing"));
            default -> plugin.messageService().send(sender, "gem.reroll.usage");
        }
        return true;
    }

    private void sendRerollError(CommandSender sender, String errorKey) {
        plugin.messageService().send(sender,
                errorKey == null || errorKey.isBlank() ? "gui.gem.reroll_failed" : errorKey);
    }

    private String operationTypeText(GemRerollSessionService.OperationType type) {
        return type == null ? "-" : type.name().toLowerCase(Locale.ROOT);
    }

    private boolean handleClearState(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        if (!plugin.stateService().hasStoredLayer(itemStack)) {
            plugin.messageService().send(sender, "command.clearstate.no_layer");
            return true;
        }
        ItemStack rebuilt = plugin.stateService().clearGemLayer(itemStack);
        if (rebuilt == null) {
            plugin.messageService().send(sender, "command.clearstate.apply_failed");
            return true;
        }
        player.getInventory().setItemInMainHand(rebuilt);
        plugin.messageService().send(sender, "command.clearstate.success");
        return true;
    }

    private void sendHelp(CommandSender sender, String label, List<String> path) {
        sendHelpPage(sender, helpRoot(label), path);
    }

    private String helpRoot(String label) {
        return "/" + (label == null || label.isBlank() ? "egem" : label);
    }

    private void sendHelpPage(CommandSender sender, String root, List<String> path) {
        if (path.isEmpty()) {
            List<String> keys = new ArrayList<>();
            keys.add("command.help_header");
            keys.add("command.help_entry_help");
            if (hasSubcommandPermission(sender, "gui")) {
                keys.add("command.help_entry_gui");
            }
            if (hasSubcommandPermission(sender, "reload")) {
                keys.add("command.help_entry_reload");
            }
            if (hasSubcommandPermission(sender, "edit")) {
                keys.add("command.help_entry_edit");
            }
            if (hasSubcommandPermission(sender, "inspect")) {
                keys.add("command.help_entry_inspect");
            }
            if (hasSubcommandPermission(sender, "reroll")) {
                keys.add("command.help_entry_reroll");
            }
            if (hasSubcommandPermission(sender, "clearstate")) {
                keys.add("command.help_entry_clearstate");
            }
            if (hasSubcommandPermission(sender, "debug")) {
                keys.add("command.help_entry_debug");
            }
            sendHelpLines(sender, root, keys.toArray(new String[0]));
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(0).toLowerCase(Locale.ROOT)) {
            case "gui" -> sendGuiHelp(sender, root);
            case "reroll" -> sendRerollHelp(sender, root);
            case "inspect" -> sendPlayerHelp(sender, root, "inspect", "inspect",
                    "command.help_inspect_header", "command.help_value_desc_inspect");
            case "debug" -> sendDebugHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of());
        }
    }

    private void sendGuiHelp(CommandSender sender, String root) {
        List<String> keys = new ArrayList<>();
        keys.add("command.help_gui_header");
        if (hasSubcommandPermission(sender, "gui")) {
            keys.add("command.help_gui_entry_inlay");
            keys.add("command.help_gui_entry_upgrade");
            keys.add("command.help_gui_entry_extract");
            keys.add("command.help_gui_entry_reroll");
            keys.add("command.help_gui_entry_recalibrate");
            keys.add("command.help_gui_entry_open");
        }
        sendHelpLines(sender, root, keys.toArray(new String[0]));
        sendHelpFooter(sender);
    }

    private void sendRerollHelp(CommandSender sender, String root) {
        List<String> keys = new ArrayList<>();
        keys.add("command.help_reroll_header");
        if (hasSubcommandPermission(sender, "reroll")) {
            keys.add("command.help_reroll_entry_full");
            keys.add("command.help_reroll_entry_value");
            keys.add("command.help_reroll_entry_confirm");
            keys.add("command.help_reroll_entry_cancel");
            keys.add("command.help_reroll_entry_status");
        }
        sendHelpLines(sender, root, keys.toArray(new String[0]));
        sendHelpFooter(sender);
    }

    private void sendDebugHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() < 2) {
            List<String> keys = new ArrayList<>();
            keys.add("command.help_debug_header");
            if (hasSubcommandPermission(sender, "debug")) {
                keys.add("command.help_debug_entry_status");
                keys.add("command.help_debug_entry_on");
                keys.add("command.help_debug_entry_off");
                keys.add("command.help_debug_entry_player");
                keys.add("command.help_debug_entry_module");
                keys.add("command.help_debug_entry_action");
            }
            sendHelpLines(sender, root, keys.toArray(new String[0]));
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(1).toLowerCase(Locale.ROOT)) {
            case "player" -> sendPlayerHelp(sender, root, "debug", "debug player",
                    "command.help_debug_player_header", "command.help_value_desc_debug_player");
            case "module" -> sendModuleHelp(sender, root);
            case "action" -> sendDebugActionHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of("debug"));
        }
    }

    private void sendPlayerHelp(CommandSender sender, String root, String owner, String command,
            String headerKey, String descKey) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message(headerKey, Map.of("root", root)));
        if (hasSubcommandPermission(sender, owner)) {
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
        }
        sendHelpFooter(sender);
    }

    private void sendModuleHelp(CommandSender sender, String root) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message("command.help_debug_module_header", Map.of("root", root)));
        if (hasSubcommandPermission(sender, "debug")) {
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
        }
        sendHelpFooter(sender);
    }

    private void sendDebugActionHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 3 && "run".equalsIgnoreCase(path.get(2))) {
            List<String> runKeys = new ArrayList<>();
            runKeys.add("command.help_debug_action_run_header");
            if (hasSubcommandPermission(sender, "debug")) {
                runKeys.add("command.help_debug_action_run_entry_shadow");
                runKeys.add("command.help_debug_action_run_entry_execute");
            }
            sendHelpLines(sender, root, runKeys.toArray(new String[0]));
            sendHelpFooter(sender);
            return;
        }
        List<String> keys = new ArrayList<>();
        keys.add("command.help_debug_action_header");
        if (hasSubcommandPermission(sender, "debug")) {
            keys.add("command.help_debug_action_entry_run");
            keys.add("command.help_debug_action_entry_check");
            keys.add("command.help_debug_action_entry_registry");
        }
        sendHelpLines(sender, root, keys.toArray(new String[0]));
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

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DEBUG) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }
}
