package emaki.jiuwu.craft.forge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.api.command.CommandKeys;
import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport.Guard;

final class ForgeCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakiforge";
    private static final String PERMISSION_BOOK = PERMISSION_ROOT + ".book";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";
    private static final String PERMISSION_EDIT = PERMISSION_ROOT + ".edit";

    private final EmakiForgePlugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final ThreadOwnership threadOwnership;

    ForgeCommandRouter(EmakiForgePlugin plugin,
                       ExecutionDispatcher executionDispatcher,
                       ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
        this.threadOwnership = threadOwnership;
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
            case "forge" -> handleForge(sender);
            case "book" -> handleBook(sender);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender, args);
            case "edit" -> handleEdit(sender, args);
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
            return CommandTabHelper.completeSubcommands(
                    List.of("help", "forge", "book", "reload", "list", "edit", "debug"),
                    args[0]);
        }
        if (args.length >= 2 && "debug".equalsIgnoreCase(args[0])) {
            List<String> debugSuggestions = new ArrayList<>(
                    plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length)));
            if (args.length == 2 && "stats".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                debugSuggestions.add("stats");
            }
            return debugSuggestions.stream().distinct().toList();
        }
        if (args.length == 2 && "list".equalsIgnoreCase(args[0])) {
            for (String sub : List.of("recipe", "recipes")) {
                if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(sub);
                }
            }
        }
        if (args.length == 2 && "edit".equalsIgnoreCase(args[0]) && plugin.editorModule() != null) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String id : plugin.editorModule().knownIds()) {
                if (id.startsWith(prefix)) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    private boolean handleForge(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        return plugin.forgeGuiService().openGeneralForgeGui(player);
    }

    private boolean handleBook(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_BOOK) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        return plugin.recipeBookGuiService().openRecipeBook(player);
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.messageService().send(sender, "general.reloading");
        long startTime = System.currentTimeMillis();
        plugin.reloadPluginStateAsync(true).whenComplete((result, throwable) -> runForSender(sender, () -> {
            long elapsedMs = System.currentTimeMillis() - startTime;
            if (throwable != null || result == null || !result.successful()) {
                plugin.messageService().send(sender, "general.reload_fail");
                if (result != null && !result.detail().isBlank()) {
                    plugin.messageService().sendRaw(sender, "<red>" + result.detail() + "</red>");
                }
                plugin.messageService().sendRaw(sender, "<gray>重载耗时: <white>" + elapsedMs + "ms</white></gray>");
                return;
            }
            plugin.messageService().send(sender, "general.reload_success");
            plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of(
                    "recipes", result.recipes(),
                    "guis", result.guiTemplates()
            )));
            plugin.messageService().sendRaw(sender, "<gray>重载耗时: <white>" + elapsedMs + "ms</white></gray>");
        }));
        return true;
    }

    private void runForSender(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            if (threadOwnership.isEntityOwned(player)) {
                task.run();
            } else {
                executionDispatcher.runEntity(plugin, player, task, () -> {
                });
            }
            return;
        }
        if (threadOwnership.isGlobalOwned()) {
            task.run();
        } else {
            executionDispatcher.runGlobal(plugin, task);
        }
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
            plugin.editorModule().openBrowser(player);
            return true;
        }
        if (plugin.editorModule().openById(player, args[1]) == null) {
            plugin.messageService().send(sender, "editor.open_failed",
                    Map.of("id", Texts.normalizeId(args[1])));
        }
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "recipe", "recipes" -> {
                plugin.messageService().sendRaw(sender, plugin.messageService().message("command.list.recipes_header", Map.of("count", plugin.recipeLoader().all().size())));
                plugin.recipeLoader().all().forEach((id, recipe)
                        -> plugin.messageService().sendRaw(sender, plugin.messageService().message("command.list.recipe_line", Map.of("id", id, "name", recipe.displayName()))));
            }
            default -> plugin.messageService().send(sender, "general.invalid_args");
        }
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DEBUG) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (args.length >= 2 && ("stats".equalsIgnoreCase(args[1]) || "status".equalsIgnoreCase(args[1]))) {
            var runtime = plugin.runtimeMetrics().snapshot();
            var recipeReport = plugin.recipeLoader().report();
            plugin.messageService().sendRaw(sender, plugin.messageService().message(
                    "command.debug.runtime",
                    runtime.debugValues(plugin.runtimeStatus(), plugin.runtimeSnapshot().guiState())
            ));
            plugin.messageService().sendRaw(sender, plugin.messageService().message(
                    "command.debug.recipe_load",
                    Map.ofEntries(
                            Map.entry("generation", recipeReport.generation()),
                            Map.entry("files", recipeReport.discovered()),
                            Map.entry("parsed", recipeReport.parsed()),
                            Map.entry("skipped", recipeReport.skipped()),
                            Map.entry("registered", recipeReport.registered()),
                            Map.entry("duplicates", recipeReport.duplicates()),
                            Map.entry("executable", recipeReport.executable()),
                            Map.entry("gui_visible", recipeReport.guiVisible()),
                            Map.entry("issues", recipeReport.issueCount()),
                            Map.entry("warnings", recipeReport.warningCount()),
                            Map.entry("hash", recipeReport.fileSummaryHash()),
                            Map.entry("duration_ms", String.format(Locale.ROOT, "%.3f", recipeReport.durationNanos() / 1_000_000D)),
                            Map.entry("source_statuses", recipeReport.sourceStatuses())
                    )
            ));
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }

    private void sendHelp(CommandSender sender, String label) {
        sendHelpPage(sender, helpRoot(label), List.of());
    }

    private String helpRoot(String label) {
        return "/" + (label == null || label.isBlank() ? "emakiforge" : label);
    }

    private void sendHelpPage(CommandSender sender, String root, List<String> path) {
        if (path.isEmpty()) {
            sendHelpLines(sender, root, "command.help_header",
                    "command.help_entry_forge", "command.help_entry_book", "command.help_entry_reload",
                    "command.help_entry_list", "command.help_entry_edit", "command.help_entry_debug",
                    "command.help_entry_help");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(0).toLowerCase(Locale.ROOT)) {
            case "list" -> {
                sendHelpLines(sender, root, "command.help_list_header",
                        "command.help_list_entry_recipe");
                sendHelpFooter(sender);
            }
            case "edit" -> sendValuePage(sender, root, "command.help_edit_id_header",
                    "command.help_value_desc_edit_id", "edit", editorIds());
            case "debug" -> sendDebugHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of());
        }
    }

    private void sendDebugHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() < 2) {
            sendHelpLines(sender, root, "command.help_debug_header",
                    "command.help_debug_entry_stats", "command.help_debug_entry_on", "command.help_debug_entry_off",
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
}
