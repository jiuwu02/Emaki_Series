package emaki.jiuwu.craft.corelib.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionEngine;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.PhaseContract;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.PipelineOutcome;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.PipelineTaskService;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckMessages;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckReport;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class CoreLibCommandRouter implements TabExecutor {

    private static final String PERMISSION_RELOAD = "emakicorelib.reload";
    private static final String PERMISSION_ADMIN = "emakicorelib.admin";
    private static final List<String> SUB_COMMANDS = List.of("help", "reload", "check", "debug", "action", "actions");
    private static final List<String> ACTION_MODES = List.of("list", "run");
    private static final List<String> CHECK_MODES = List.of("report", "--fix");
    private static final List<String> DEBUG_MODES = List.of("loops", "all");
    private static final List<String> DEBUG_ALL_MODES = List.of("on", "off", "status");
    private static final List<String> LOOP_DEBUG_MODES = List.of("list", "player", "key", "cancel", "cancel-player");

    private final EmakiCoreLibPlugin plugin;
    private final ExecutionDispatcher executionDispatcher;

    public CoreLibCommandRouter(EmakiCoreLibPlugin plugin, ExecutionDispatcher executionDispatcher) {
        this.plugin = plugin;
        this.executionDispatcher = java.util.Objects.requireNonNull(executionDispatcher, "executionDispatcher");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "help" -> {
                sendHelp(sender, label);
                yield true;
            }
            case "reload" -> handleReload(sender);
            case "check" -> handleCheck(sender, args);
            case "debug" -> handleDebug(sender, args);
            case "action", "actions" -> handleAction(sender, args);
            default -> {
                sendHelp(sender, label);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
            for (String subCommand : SUB_COMMANDS) {
                if (subCommand.startsWith(prefix)) {
                    result.add(subCommand);
                }
            }
        } else if (args.length == 2 && "check".equalsIgnoreCase(args[0])) {
            complete(args[1], CHECK_MODES, result);
            complete(args[1], plugin.configPrecheckService().registry().moduleIds(), result);
        } else if (args.length == 2 && isActionCommand(args[0])) {
            complete(args[1], ACTION_MODES, result);
            complete(args[1], stageIds(), result);
        } else if (args.length == 3 && isActionCommand(args[0]) && "run".equalsIgnoreCase(args[1])) {
            complete(args[2], stageIds(), result);
        } else if (args.length == 2 && "debug".equalsIgnoreCase(args[0])) {
            complete(args[1], DEBUG_MODES, result);
        } else if (args.length == 3 && "debug".equalsIgnoreCase(args[0]) && "all".equalsIgnoreCase(args[1])) {
            complete(args[2], DEBUG_ALL_MODES, result);
        } else if (args.length == 3 && "debug".equalsIgnoreCase(args[0]) && "loops".equalsIgnoreCase(args[1])) {
            complete(args[2], LOOP_DEBUG_MODES, result);
        } else if (args.length == 4 && "debug".equalsIgnoreCase(args[0]) && "loops".equalsIgnoreCase(args[1]) && "player".equalsIgnoreCase(args[2])) {
            String prefix = args[3].toLowerCase(java.util.Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                    result.add(player.getName());
                }
            }
        }
        return result;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            sendLang(sender, "command.no_permission_reload");
            return true;
        }
        if (plugin.reloadActionSystem()) {
            sendLang(sender, "command.reload_success");
        } else {
            sendLang(sender, "command.reload_failed_precheck");
        }
        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            sendLang(sender, "command.no_permission_admin");
            return true;
        }
        if (args.length >= 2 && "--fix".equalsIgnoreCase(args[1])) {
            sendLang(sender, "command.check_fix_unavailable");
            return true;
        }
        ConfigPrecheckReport report = args.length >= 2 && !"report".equalsIgnoreCase(args[1])
                ? plugin.configPrecheckService().checkModule(plugin.configModel(), args[1])
                : (args.length >= 2 ? plugin.configPrecheckService().lastReport() : plugin.configPrecheckService().checkAll(plugin.configModel()));
        sendReport(sender, report);
        return true;
    }

    private boolean handleAction(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            sendLang(sender, "command.no_permission_admin");
            return true;
        }
        StageRegistry registry = plugin.stageRegistry();
        if (registry == null || plugin.actionEngine() == null) {
            sendLang(sender, "command.action_unavailable");
            return true;
        }
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            sendStageList(sender, registry);
            return true;
        }
        int lineStart = "run".equalsIgnoreCase(args[1]) ? 2 : 1;
        String rawLine = joinArguments(args, lineStart);
        if (Texts.isBlank(rawLine)) {
            sendLang(sender, "command.action_usage");
            return true;
        }
        runPipelineLine(sender, rawLine);
        return true;
    }

    /**
     * Compiles and runs one pipeline line typed at the console or in chat.
     *
     * <p>Compiled first so a syntax or unknown-stage problem is reported with its own diagnostic instead of
     * surfacing as a generic run failure. Only the first diagnostic is shown: the compiler reports every
     * problem it finds on a line, and the rest are usually consequences of the first.</p>
     */
    private void runPipelineLine(CommandSender sender, String rawLine) {
        ActionEngine engine = plugin.actionEngine();
        Player player = sender instanceof Player resolvedPlayer ? resolvedPlayer : null;
        PipelineContext context = plugin.actionLineRunner(plugin)
                .context(player, "command.action", false, Map.of(
                        "sender", sender.getName(),
                        "player", player == null ? "" : player.getName(),
                        "action_line", rawLine
                ));
        PhaseContract phase = PhaseContract.declared(context.phase(), Set.copyOf(context.presentKeys()),
                context.variables().keySet(), !context.targets().isEmpty());
        ActionEngine.Result compiled = engine.compile(rawLine, phase);
        if (!compiled.successful() || compiled.pipeline() == null) {
            sendLang(sender, "command.action_compile_failed", Map.of(
                    "line", rawLine,
                    "reason", plugin.messageService().renderFirstDiagnostic(compiled.diagnostics())
            ));
            return;
        }
        sendLang(sender, "command.action_execute_started", Map.of("line", rawLine));
        engine.run(plugin, compiled.pipeline(), context).whenComplete((outcome, throwable) ->
                dispatchSender(sender, () -> sendPipelineOutcome(sender, outcome, throwable))
        );
    }

    /**
     * Lists the registered pipeline stages, grouped by role.
     *
     * <p>Grouped because a stage id is only valid in one position of a line, so a flat list would not tell
     * an operator where the id they are looking at can actually be used.</p>
     */
    private void sendStageList(CommandSender sender, StageRegistry registry) {
        Map<CoreStageKind, List<String>> byKind = registry.allIds();
        int total = byKind.values().stream().mapToInt(List::size).sum();
        sendLang(sender, "command.action_list_header", Map.of("count", String.valueOf(total)));
        if (total == 0) {
            sendLang(sender, "command.action_list_empty");
            return;
        }
        for (CoreStageKind kind : CoreStageKind.values()) {
            List<String> ids = byKind.getOrDefault(kind, List.of());
            for (String id : ids) {
                plugin.messageService().sendRaw(sender, plugin.messageService().message("command.action_list_item", Map.of(
                        "id", id,
                        "kind", Texts.lower(kind.name()),
                        "owner", emptyAsDash(ownerNameOf(registry, kind, id))
                )));
            }
        }
    }

    private String ownerNameOf(StageRegistry registry, CoreStageKind kind, String id) {
        return switch (kind) {
            case SOURCE -> registry.sources().ownerNameOf(id);
            case GATE -> registry.gates().ownerNameOf(id);
            case ACTION -> registry.actions().ownerNameOf(id);
        };
    }

    private void dispatchSender(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            executionDispatcher.runEntity(plugin, player, task, () -> { });
        } else {
            executionDispatcher.runGlobal(plugin, task);
        }
    }

    /**
     * Reports how a manually run pipeline ended.
     *
     * <p>Names the stage that failed rather than only the overall status, because a line typed by hand is
     * usually being debugged and the failing stage is the whole answer.</p>
     */
    private void sendPipelineOutcome(CommandSender sender, PipelineOutcome outcome, Throwable throwable) {
        if (throwable != null) {
            sendLang(sender, "command.action_execute_failed", Map.of(
                    "stage", "-",
                    "error", Texts.toStringSafe(throwable.getMessage())
            ));
            return;
        }
        if (outcome == null) {
            sendLang(sender, "command.action_execute_failed", Map.of("stage", "-", "error", "No result returned."));
            return;
        }
        if (outcome.status() == PipelineOutcome.Status.FAILURE) {
            PipelineOutcome.StageResult failure = firstFailedStage(outcome);
            // Only the pipeline-level outcome carries placeholder arguments; StageResult has just a key.
            // Prefer the stage's key when the pipeline did not set one, so the more specific reason wins.
            String reasonKey = Texts.isBlank(outcome.reasonKey()) && failure != null
                    ? failure.reasonKey()
                    : outcome.reasonKey();
            sendLang(sender, "command.action_execute_failed", Map.of(
                    "stage", failure == null ? "-" : Texts.toStringSafe(failure.stageId()),
                    "error", plugin.messageService().renderDiagnostic(reasonKey, outcome.args())
            ));
            return;
        }
        sendLang(sender, "command.action_execute_success", Map.of(
                "status", Texts.lower(outcome.status().name()),
                "stages", String.valueOf(outcome.stageResults().size())
        ));
    }

    private PipelineOutcome.StageResult firstFailedStage(PipelineOutcome outcome) {
        for (PipelineOutcome.StageResult stage : outcome.stageResults()) {
            if (stage != null && stage.status() == PipelineOutcome.Status.FAILURE) {
                return stage;
            }
        }
        return null;
    }

    private List<String> stageIds() {
        StageRegistry registry = plugin.stageRegistry();
        if (registry == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        registry.allIds().values().forEach(ids::addAll);
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    private String joinArguments(String[] args, int startIndex) {
        if (args == null || startIndex >= args.length) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = startIndex; index < args.length; index++) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    private boolean isActionCommand(String value) {
        return "action".equalsIgnoreCase(value) || "actions".equalsIgnoreCase(value);
    }

    private String emptyAsDash(String value) {
        return Texts.isBlank(value) ? "-" : value;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            sendLang(sender, "command.no_permission_admin");
            return true;
        }
        if (args.length >= 2 && "all".equalsIgnoreCase(args[1])) {
            return handleGlobalDebug(sender, args);
        }
        if (args.length < 2 || !"loops".equalsIgnoreCase(args[1])) {
            sendHelp(sender, "corelib");
            return true;
        }
        PipelineTaskService tasks = plugin.pipelineTaskService();
        if (tasks == null) {
            sendLang(sender, "command.action_unavailable");
            return true;
        }
        String mode = args.length >= 3 ? args[2].toLowerCase(java.util.Locale.ROOT) : "list";
        switch (mode) {
            case "player" -> {
                Player player = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : null;
                if (player == null) {
                    sendLang(sender, "debug.command.player_not_found", Map.of("player", args.length >= 4 ? args[3] : ""));
                } else {
                    sendTaskSnapshots(sender, tasks.snapshotsByPlayer(player.getUniqueId()));
                }
            }
            case "key" -> sendTaskSnapshots(sender, tasks.snapshotsByKey(args.length >= 4 ? args[3] : ""));
            case "cancel" -> {
                String key = args.length >= 4 ? args[3] : "";
                // Exact match, matching what this command did under v1: an operator cancelling by a key
                // they typed should not also take out everything that happens to share its prefix.
                int cancelled = tasks.stop(key, false);
                if (cancelled > 0) {
                    sendLang(sender, "command.loop_cancelled", Map.of("key", key));
                } else {
                    sendLang(sender, "command.loop_not_found", Map.of("key", key));
                }
            }
            case "cancel-player" -> {
                Player player = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : null;
                if (player == null) {
                    sendLang(sender, "debug.command.player_not_found", Map.of("player", args.length >= 4 ? args[3] : ""));
                } else {
                    int count = tasks.stopByPlayer(player.getUniqueId());
                    sendLang(sender, "command.loop_player_cancelled", Map.of("player", player.getName(), "count", String.valueOf(count)));
                }
            }
            default -> sendTaskSnapshots(sender, tasks.snapshots());
        }
        return true;
    }

    private boolean handleGlobalDebug(CommandSender sender, String[] args) {
        String mode = args.length >= 3 ? args[2].toLowerCase(java.util.Locale.ROOT) : "status";
        switch (mode) {
            case "on" -> {
                plugin.setGlobalDebugEnabled(true);
                sendLang(sender, "debug.command.global_all_enabled");
            }
            case "off" -> {
                plugin.setGlobalDebugEnabled(false);
                sendLang(sender, "debug.command.global_all_disabled");
            }
            default -> sendLang(sender, plugin.globalDebugEnabled()
                    ? "debug.command.global_all_enabled"
                    : "debug.command.global_all_disabled");
        }
        return true;
    }

    private void sendReport(CommandSender sender, ConfigPrecheckReport report) {
        if (report == null) {
            sendLang(sender, "command.check_no_report");
            return;
        }
        ConfigPrecheckMessages.sendReport(plugin.messageService(), sender, "corelib", report);
    }

    private void sendTaskSnapshots(CommandSender sender, List<PipelineTaskService.TaskSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            sendLang(sender, "command.loop_empty");
            return;
        }
        sendLang(sender, "command.loop_header", Map.of("count", String.valueOf(snapshots.size())));
        for (PipelineTaskService.TaskSnapshot snapshot : snapshots) {
            String player = snapshot.playerUuid() == null ? "-" : snapshot.playerUuid().toString();
            plugin.messageService().sendRaw(sender, "<gray>- key=<aqua>" + snapshot.key()
                    + "</aqua> plugin=<yellow>" + snapshot.pluginName()
                    + "</yellow> index=<white>" + snapshot.index() + "/" + snapshot.times()
                    + "</white> interval=<white>" + snapshot.intervalTicks() + "t"
                    + "</white> player=<gray>" + player + "</gray></gray>");
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        String root = "/" + (label == null || label.isBlank() ? "emakicorelib" : label);
        sendLang(sender, "command.help_header");
        sendLang(sender, "command.help_reload", Map.of("root", root));
        sendLang(sender, "command.help_check", Map.of("root", root));
        sendLang(sender, "command.help_action", Map.of("root", root));
        sendLang(sender, "command.help_debug_all", Map.of("root", root));
        sendLang(sender, "command.help_debug_loops", Map.of("root", root));
    }

    private void complete(String rawPrefix, List<String> options, List<String> result) {
        String prefix = rawPrefix.toLowerCase(java.util.Locale.ROOT);
        for (String option : options) {
            if (option.startsWith(prefix)) {
                result.add(option);
            }
        }
    }

    private void sendLang(CommandSender sender, String key) {
        MessageService messageService = plugin.messageService();
        messageService.sendRaw(sender, messageService.message(key));
    }

    private void sendLang(CommandSender sender, String key, Map<String, ?> replacements) {
        MessageService messageService = plugin.messageService();
        messageService.sendRaw(sender, messageService.message(key, replacements));
    }
}
