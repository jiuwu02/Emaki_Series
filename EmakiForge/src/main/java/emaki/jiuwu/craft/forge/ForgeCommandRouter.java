package emaki.jiuwu.craft.forge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.legacy.LegacyItemSourceRewriter;
import emaki.jiuwu.craft.corelib.legacy.LegacyItemSourceRewriter.FileReport;
import emaki.jiuwu.craft.corelib.legacy.LegacyItemSourceRewriter.RunReport;
import emaki.jiuwu.craft.corelib.legacy.LegacyItemSourceRewriter.Status;
import emaki.jiuwu.craft.forge.legacy.ForgeLegacyTargets;

final class ForgeCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakiforge";
    private static final String PERMISSION_BOOK = PERMISSION_ROOT + ".book";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";

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
            sendHelp(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            case "forge" -> handleForge(sender);
            case "book" -> handleBook(sender);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender, args);
            case "convert-legacy" -> handleConvertLegacy(sender, args);
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
            for (String sub : List.of("help", "forge", "book", "reload", "list", "convert-legacy", "debug")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length >= 2 && "debug".equalsIgnoreCase(args[0])) {
            return plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 2 && "list".equalsIgnoreCase(args[0])) {
            for (String sub : List.of("recipe")) {
                if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(sub);
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
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        return plugin.recipeBookGuiService().openRecipeBook(player);
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
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

    private boolean handleConvertLegacy(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        boolean apply = args.length >= 2
                && ("confirm".equalsIgnoreCase(args[1]) || "--apply".equalsIgnoreCase(args[1]));
        RunReport report = new LegacyItemSourceRewriter(
                plugin.getDataFolder().toPath(),
                ForgeLegacyTargets.specs(),
                plugin.getLogger()).run(apply);
        var messages = plugin.messageService();
        messages.sendRaw(sender, messages.message("command.convert_legacy.header", Map.of(
                "mode", messages.message(apply
                        ? "command.convert_legacy.mode.apply"
                        : "command.convert_legacy.mode.dry_run"),
                "files", report.files().size()
        )));
        for (FileReport file : report.files()) {
            sendConvertLegacyFile(sender, file, apply);
        }
        messages.sendRaw(sender, messages.message("command.convert_legacy.summary", Map.of(
                "converted", report.count(Status.CONVERTED),
                "skipped", report.count(Status.NO_LEGACY_BLOCK),
                "conflict", report.count(Status.CONFLICT),
                "unconvertible", report.count(Status.UNCONVERTIBLE),
                "failed", report.count(Status.FAILED)
        )));
        if (!apply) {
            messages.send(sender, report.hasConvertible()
                    ? "command.convert_legacy.dry_run_hint"
                    : "command.convert_legacy.nothing_to_do");
        }
        return true;
    }

    private void sendConvertLegacyFile(CommandSender sender, FileReport file, boolean apply) {
        if (file.status() == Status.NO_LEGACY_BLOCK) {
            return;
        }
        var messages = plugin.messageService();
        messages.sendRaw(sender, messages.message("command.convert_legacy.file", Map.of(
                "file", file.fileName(),
                "status", messages.message("command.convert_legacy.status."
                        + file.status().name().toLowerCase(Locale.ROOT)),
                "detail", file.detail()
        )));
        if (apply && !file.backupName().isBlank()) {
            messages.sendRaw(sender, messages.message("command.convert_legacy.backup",
                    Map.of("backup", file.backupName())));
        }
        if (!apply) {
            for (String line : file.diff()) {
                messages.sendRaw(sender, messages.message("command.convert_legacy.diff_line",
                        Map.of("line", line)));
            }
        }
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

    private boolean handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
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
            plugin.messageService().send(sender, "general.no_permission");
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

    private void sendHelp(CommandSender sender) {
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.header"));
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("help", plugin.messageService().message("command.help.commands.help"));
        lines.put("forge", plugin.messageService().message("command.help.commands.forge"));
        lines.put("book", plugin.messageService().message("command.help.commands.book"));
        lines.put("reload", plugin.messageService().message("command.help.commands.reload"));
        lines.put("list <type>", plugin.messageService().message("command.help.commands.list <type>"));
        lines.put("debug [player|module|on|off]", plugin.messageService().message("command.help.commands.debug"));
        lines.forEach((commandName, description)
                -> plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.line", Map.of("cmd", commandName, "desc", description))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.footer"));
    }
}
