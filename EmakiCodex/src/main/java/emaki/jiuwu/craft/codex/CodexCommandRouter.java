package emaki.jiuwu.craft.codex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.command.CommandKeys;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport.Guard;

final class CodexCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakicodex";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";
    private static final String PERMISSION_EDIT = PERMISSION_ROOT + ".edit";

    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";

    private static final List<String> SUBCOMMANDS =
            List.of("help", "codex", "open", "reload", "edit", "grant", "revoke", "admin", "debug");
    private static final List<String> ADMIN_ACTIONS = List.of("unlock", "reset");

    private final EmakiCodexPlugin plugin;

    CodexCommandRouter(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelpPage(sender, helpRoot(label), Arrays.asList(args).subList(1, args.length));
                yield true;
            }
            case "codex", "open" -> openCodexGui(sender);
            case "reload" -> handleReload(sender);
            case "edit" -> handleEdit(sender, args);
            case "grant" -> handleAdvancement(sender, args, true);
            case "revoke" -> handleAdvancement(sender, args, false);
            case "admin" -> routeAdmin(sender, args);
            case "debug" -> handleDebug(sender, args);
            default -> {
                plugin.messageService().send(sender, "general.unknown_command");
                yield true;
            }
        };
    }

    private boolean openCodexGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!hasAdminOr(sender, PERMISSION_USE)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        plugin.executionDispatcher().runEntity(plugin, player, () -> {
            if (!plugin.codexGuiService().open(player)) {
                plugin.messageService().send(player, "gui.open_failed");
            }
        }, () -> plugin.messageService().send(player, "gui.open_failed"));
        return true;
    }

    private boolean routeAdmin(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "unlock" -> unlockEntry(sender, args);
            case "reset" -> resetEntries(sender, args);
            default -> {
                plugin.messageService().send(sender, "general.unknown_command");
                yield true;
            }
        };
    }

    private boolean unlockEntry(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found", Map.of("player", args[2]));
            return true;
        }
        String[] composite = splitEntryReference(args[3]);
        if (composite == null) {
            plugin.messageService().send(sender, "command.codex.bad_reference", Map.of("entry", args[3]));
            return true;
        }
        EmakiResult<Unit> result = plugin.codexEntryService().unlock(target, composite[0], composite[1]);
        if (!result.isSuccess()) {
            plugin.messageService().send(sender, "command.codex.failed", Map.of(
                    "entry", args[3],
                    "reason", describeFailure(result)));
            return true;
        }
        plugin.messageService().send(sender, "command.codex.unlocked", Map.of(
                "player", target.getName(),
                "entry", args[3]));
        return true;
    }

    private boolean resetEntries(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found", Map.of("player", args[2]));
            return true;
        }
        String reference = args.length >= 4 ? args[3] : "";
        String category = "";
        String entry = "";
        if (!reference.isEmpty()) {
            String[] composite = splitEntryReference(reference);
            if (composite == null) {
                plugin.messageService().send(sender, "command.codex.bad_reference", Map.of("entry", reference));
                return true;
            }
            category = composite[0];
            entry = composite[1];
        }
        EmakiResult<Integer> result = plugin.codexEntryService().reset(target, category, entry);
        if (!result.isSuccess()) {
            plugin.messageService().send(sender, "command.codex.failed", Map.of(
                    "entry", reference.isEmpty() ? "*" : reference,
                    "reason", describeReason(result.reasonKey())));
            return true;
        }
        plugin.messageService().send(sender, "command.codex.reset", Map.of(
                "player", target.getName(),
                "count", result.optionalValue().orElse(0)));
        return true;
    }

    private String[] splitEntryReference(String reference) {
        String normalized = Texts.trim(reference);
        int separator = normalized.indexOf('/');
        if (separator <= 0 || separator >= normalized.length() - 1) {
            return null;
        }
        return new String[] { normalized.substring(0, separator), normalized.substring(separator + 1) };
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

    private boolean handleReload(CommandSender sender) {
        if (!hasAdminOr(sender, PERMISSION_RELOAD)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.messageService().send(sender, "general.reloading");
        long startTime = System.currentTimeMillis();
        plugin.reloadPluginState();
        long elapsedMs = System.currentTimeMillis() - startTime;
        plugin.messageService().send(sender, "general.reload_success");
        plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of(
                "advancements", plugin.advancementRegistrar().size()
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message(
                "general.reload_duration", Map.of("millis", elapsedMs)));
        return true;
    }

    private boolean handleAdvancement(CommandSender sender, String[] args, boolean grant) {
        if (!hasAdmin(sender)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (args.length < 3) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found", Map.of("player", args[1]));
            return true;
        }
        String advancementId = args[2];

        runOnTargetOwner(target, () -> grant
                ? plugin.advancementService().grant(target, advancementId)
                : plugin.advancementService().revoke(target, advancementId))
                .whenComplete((result, throwable) -> {
                    if (throwable != null || result == null) {
                        sendToSender(sender, () -> plugin.messageService().send(sender,
                                "command.advancement.failed", Map.of(
                                        "advancement", advancementId,
                                        "reason", throwable == null
                                                ? plugin.messageService().message("command.advancement.reason_unknown")
                                                : Texts.toStringSafe(throwable.getMessage()))));
                        return;
                    }
                    if (!result.isSuccess()) {
                        sendToSender(sender, () -> plugin.messageService().send(sender,
                                "command.advancement.failed", Map.of(
                                        "advancement", advancementId,
                                        "reason", describeFailure(result))));
                        return;
                    }
                    String key = grant ? "command.grant.done" : "command.revoke.done";
                    sendToSender(sender, () -> plugin.messageService().send(sender, key, Map.of(
                            "player", target.getName(),
                            "advancement", advancementId)));
                });
        return true;
    }

    private String describeFailure(EmakiResult<Unit> result) {
        return describeReason(result == null ? "" : result.reasonKey());
    }

    private String describeReason(String rawReasonKey) {
        String reasonKey = Texts.toStringSafe(rawReasonKey);
        if (Texts.isBlank(reasonKey)) {
            return plugin.messageService().message("command.advancement.reason_unknown");
        }
        return plugin.messageService().messageOrFallback(reasonKey, reasonKey);
    }

    private CompletableFuture<EmakiResult<Unit>> runOnTargetOwner(Player target,
            Supplier<EmakiResult<Unit>> operation) {
        CompletableFuture<EmakiResult<Unit>> future = new CompletableFuture<>();
        if (target == null || !target.isOnline()) {
            future.complete(EmakiResult.targetOffline());
            return future;
        }
        Runnable task = () -> {
            try {
                EmakiResult<Unit> result = operation.get();
                future.complete(result == null ? EmakiResult.unavailable() : result);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        };
        try {
            if (plugin.threadOwnership() != null && plugin.threadOwnership().isEntityOwned(target)) {
                task.run();
                return future;
            }
            var scheduled = plugin.executionDispatcher().runEntity(plugin, target, task,
                    () -> future.complete(EmakiResult.unavailable()));
            if (scheduled == null) {
                future.complete(EmakiResult.unavailable());
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private void sendToSender(CommandSender sender, Runnable task) {
        try {
            if (sender instanceof Player player) {
                if (plugin.threadOwnership() != null && plugin.threadOwnership().isEntityOwned(player)) {
                    task.run();
                } else {
                    plugin.executionDispatcher().runEntity(plugin, player, task, () -> { });
                }
            } else {
                plugin.executionDispatcher().runGlobal(plugin, task);
            }
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING,
                    plugin.messageService().message("console.command_dispatch_failed", Map.of(
                            "sender", sender.getName(),
                            "cause", throwable)),
                    throwable);
        }
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!hasAdminOr(sender, PERMISSION_DEBUG)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }

    private boolean hasAdminOr(CommandSender sender, String permission) {
        return hasAdmin(sender) || sender.hasPermission(permission);
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission(PERMISSION_ADMIN)
                || (plugin.appConfig().opBypass() && sender.isOp());
    }

    private void sendHelp(CommandSender sender, String label) {
        sendHelpPage(sender, helpRoot(label), List.of());
    }

    private String helpRoot(String label) {
        return "/" + (label == null || label.isBlank() ? "codex" : label);
    }

    private void sendHelpPage(CommandSender sender, String root, List<String> path) {
        if (path.isEmpty()) {
            sendHelpLines(sender, root, "command.help_header",
                    "command.help_entry_codex", "command.help_entry_reload", "command.help_entry_edit",
                    "command.help_entry_grant", "command.help_entry_revoke", "command.help_entry_admin",
                    "command.help_entry_debug", "command.help_entry_help");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(0).toLowerCase(Locale.ROOT)) {
            case "admin" -> sendAdminHelp(sender, root, path);
            case "edit" -> sendValuePage(sender, root, "command.help_edit_id_header",
                    "command.help_value_desc_edit_id", "edit", editorIds());
            case "debug" -> sendDebugHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of());
        }
    }

    private void sendAdminHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 2 && "reset".equalsIgnoreCase(path.get(1))) {
            sendValuePage(sender, root, "command.help_admin_reset_player_header",
                    "command.help_value_desc_admin_reset_player", "admin reset", onlinePlayerNames());
            return;
        }
        sendHelpLines(sender, root, "command.help_admin_header",
                "command.help_admin_entry_unlock", "command.help_admin_entry_reset");
        sendHelpFooter(sender);
    }

    private void sendDebugHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() < 2) {
            sendHelpLines(sender, root, "command.help_debug_header",
                    "command.help_debug_entry_status", "command.help_debug_entry_on", "command.help_debug_entry_off",
                    "command.help_debug_entry_player", "command.help_debug_entry_module", "command.help_debug_entry_action");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(1).toLowerCase(Locale.ROOT)) {
            case "player" -> sendValuePage(sender, root, "command.help_debug_player_header",
                    "command.help_value_desc_debug_player", "debug player", onlinePlayerNames());
            case "action" -> sendDebugActionHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of("debug"));
        }
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

    private void sendValuePage(CommandSender sender, String root, String headerKey, String descKey,
            String command, List<String> values) {
        sendHelpLine(sender, plugin.messageService().message(headerKey, Map.of("root", root)));
        if (values.isEmpty()) {
            sendHelpLine(sender, plugin.messageService().message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String value : values) {
            sendValueEntry(sender, value,
                    plugin.messageService().message(descKey, Map.of("value", value)),
                    root + " " + command + " " + value);
        }
        sendHelpFooter(sender);
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private List<String> editorIds() {
        List<String> ids = new ArrayList<>();
        if (plugin.editorModule() != null) {
            ids.addAll(plugin.editorModule().knownIds());
        }
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    private void sendValueEntry(CommandSender sender, String value, String desc, String run) {
        sendHelpLine(sender, plugin.messageService().message("command.help_value_entry", Map.of(
                "value", value,
                "desc", desc,
                "run", run)));
    }

    private void sendHelpLines(CommandSender sender, String root, String... keys) {
        Map<String, ?> replacements = Map.of("root", root);
        for (String key : keys) {
            sendHelpLine(sender, plugin.messageService().message(key, replacements));
        }
    }

    private void sendHelpLine(CommandSender sender, String line) {
        plugin.messageService().sendComponent(sender, plugin.messageService().render(line));
    }

    private void sendHelpFooter(CommandSender sender) {
        sendHelpLine(sender, plugin.messageService().message("command.help_footer"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandTabHelper.completeSubcommands(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "grant", "revoke" -> CommandTabHelper.completeOnlinePlayers(args[1]);
                case "admin" -> CommandTabHelper.completeSubcommands(ADMIN_ACTIONS, args[1]);
                case "edit" -> {
                    if (plugin.editorModule() == null) {
                        yield List.of();
                    }
                    yield CommandTabHelper.filterByPrefix(plugin.editorModule().knownIds(), args[1]);
                }
                case "debug" -> plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
                default -> List.of();
            };
        }
        if (args.length >= 2 && "debug".equals(sub)) {
            return plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 3 && ("grant".equals(sub) || "revoke".equals(sub))) {
            Set<String> keys = plugin.advancementRegistrar().registered().keySet();
            List<String> candidates = new ArrayList<>(keys);
            String namespace = plugin.getName().toLowerCase(Locale.ROOT);
            for (String key : keys) {
                int colon = key.indexOf(':');
                if (colon > 0 && namespace.equals(key.substring(0, colon))) {
                    candidates.add(key.substring(colon + 1));
                }
            }
            return CommandTabHelper.filterByPrefix(candidates, args[2]).stream().distinct().toList();
        }
        if ("admin".equals(sub) && ADMIN_ACTIONS.contains(args[1].toLowerCase(Locale.ROOT))) {
            if (args.length == 3) {
                return CommandTabHelper.completeOnlinePlayers(args[2]);
            }
            if (args.length == 4) {
                return CommandTabHelper.filterByPrefix(entryReferences(), args[3]);
            }
        }
        return List.of();
    }

    private List<String> entryReferences() {
        List<String> references = new ArrayList<>();
        plugin.codexCategoryLoader().all().forEach((categoryId, category) ->
                category.entries().keySet().forEach(entryId -> references.add(categoryId + "/" + entryId)));
        return List.copyOf(references);
    }
}
