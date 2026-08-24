package emaki.jiuwu.craft.gem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
import emaki.jiuwu.craft.gem.legacy.GemLegacyEntry;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.gem.service.GemGuiMode;
import emaki.jiuwu.craft.gem.service.GemRerollSessionService;

final class GemCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakigem";
    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";

    private final EmakiGemPlugin plugin;

    GemCommandRouter(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            case "gui" -> handleGuiCommand(sender, args);
            case "reload" -> handleReload(sender);
            case "inspect" -> handleInspect(sender, args);
            case "reroll" -> handleReroll(sender, args);
            case "clearstate" -> handleClearState(sender);
            case "convert-legacy" -> GemLegacyEntry.handle(plugin, sender, args, PERMISSION_ADMIN);
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
            for (String sub : List.of("help", "gui", "reload", "inspect", "reroll", "clearstate", "convert-legacy", "debug")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length >= 2 && "debug".equalsIgnoreCase(args[0])) {
            return plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "gui" -> {
                    for (String sub : List.of("inlay", "upgrade", "extract", "reroll", "recalibrate", "open")) {
                        if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                            result.add(sub);
                        }
                    }
                }
                case "inspect" -> result.addAll(CommandTabHelper.completeOnlinePlayers(args[1]));
                case "reroll" -> {
                    for (String sub : List.of("full", "value", "confirm", "cancel", "status")) {
                        if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                            result.add(sub);
                        }
                    }
                }
                case "convert-legacy" -> {
                    for (String sub : List.of("confirm", "--apply")) {
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

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
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

    private boolean handleGui(CommandSender sender, GemGuiMode mode) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
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
            plugin.messageService().send(sender, "general.no_permission");
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
            plugin.messageService().send(sender, "general.no_permission");
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
            plugin.messageService().send(sender, "general.no_permission");
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

    private void sendHelp(CommandSender sender) {
        var ms = plugin.messageService();
        ms.sendRaw(sender, ms.message("command.help.header"));
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("help", ms.message("command.help.desc.help"));
        lines.put("gui [inlay|upgrade|open]", ms.message("command.help.desc.gui"));
        lines.put("reload", ms.message("command.help.desc.reload"));
        lines.put("inspect [player]", ms.message("command.help.desc.inspect"));
        lines.put("reroll <full|value|confirm|cancel|status>", "Generate or accept a reroll candidate for the held gem");
        lines.put("clearstate", ms.message("command.help.desc.clearstate"));
        lines.put("convert-legacy [confirm]", ms.message("command.help.desc.convert_legacy"));
        lines.put("debug [player|module|on|off]", ms.message("command.help.desc.debug"));
        lines.forEach((name, description) -> ms.sendRaw(sender,
                ms.message("command.help.line", Map.of("cmd", name, "desc", description))));
        ms.sendRaw(sender, ms.message("command.help.footer"));
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DEBUG) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }
}
