package emaki.jiuwu.craft.corelib.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckFixResult;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckReport;
import emaki.jiuwu.craft.corelib.debug.ActionDebugRunner;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import java.util.Arrays;

public final class CoreLibCommandRouter implements TabExecutor {

    private static final String PERMISSION_RELOAD = "emakicorelib.reload";
    private static final String PERMISSION_ADMIN = "emakicorelib.admin";
    private static final List<String> SUB_COMMANDS = List.of("help", "reload", "check", "debug", "action", "actions");
    private static final List<String> ACTION_MODES = List.of("list", "run");
    private static final List<String> CHECK_MODES = List.of("report", "--fix");
    private static final List<String> DEBUG_MODES = List.of("loops", "all", "action");
    private static final List<String> DEBUG_ACTION_MODES = List.of("run", "check", "registry");
    private static final List<String> DEBUG_ACTION_RUN_MODES = List.of("shadow", "execute");
    private static final List<String> DEBUG_ALL_MODES = List.of("on", "off", "status");
    private static final List<String> LOOP_DEBUG_MODES = List.of("list", "player", "key", "cancel", "cancel-player");

    private final EmakiCoreLibPlugin plugin;
    private final ExecutionDispatcher executionDispatcher;

    public CoreLibCommandRouter(EmakiCoreLibPlugin plugin, ExecutionDispatcher executionDispatcher) {
        this.plugin = plugin;
        this.executionDispatcher = Objects.requireNonNull(executionDispatcher, "executionDispatcher");
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
            case "reload" -> handleReload(sender);
            case "check" -> handleCheck(sender, args);
            case "debug" -> handleDebug(sender, label, args);
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
            result.addAll(CommandTabHelper.completeSubcommands(SUB_COMMANDS, args[0]));
        } else if (args.length == 2 && "check".equalsIgnoreCase(args[0])) {
            complete(args[1], CHECK_MODES, result);
            complete(args[1], plugin.configPrecheckService().registry().moduleIds(), result);
        } else if (args.length == 3 && "check".equalsIgnoreCase(args[0])
                && "--fix".equalsIgnoreCase(args[1])) {
            complete(args[2], plugin.configPrecheckService().registry().moduleIds(), result);
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
        } else if (args.length == 3 && "debug".equalsIgnoreCase(args[0]) && "action".equalsIgnoreCase(args[1])) {
            complete(args[2], DEBUG_ACTION_MODES, result);
        } else if (args.length == 4 && "debug".equalsIgnoreCase(args[0]) && "action".equalsIgnoreCase(args[1])
                && "run".equalsIgnoreCase(args[2])) {
            complete(args[3], DEBUG_ACTION_RUN_MODES, result);
        } else if (args.length == 4 && "debug".equalsIgnoreCase(args[0]) && "loops".equalsIgnoreCase(args[1])
                && ("player".equalsIgnoreCase(args[2]) || "cancel-player".equalsIgnoreCase(args[2]))) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
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
            return handleCheckFix(sender, args.length >= 3 ? args[2] : null);
        }
        ConfigPrecheckReport report = args.length >= 2 && !"report".equalsIgnoreCase(args[1])
                ? plugin.configPrecheckService().checkModule(plugin.configModel(), args[1])
                : (args.length >= 2 ? plugin.configPrecheckService().lastReport() : plugin.configPrecheckService().checkAll(plugin.configModel()));
        sendReport(sender, report);
        return true;
    }

    private boolean handleCheckFix(CommandSender sender, String module) {
        ConfigPrecheckFixResult result = plugin.configPrecheckService().fix(plugin.configModel(), module);
        if (result.fixed() == 0 && result.failed() == 0) {
            sendLang(sender, "command.check_fix_none");
        } else {
            sendLang(sender, "command.check_fix_summary", Map.of(
                    "fixed", String.valueOf(result.fixed()),
                    "failed", String.valueOf(result.failed())));
            if (result.fixed() > 0) {
                sendLang(sender, "command.check_fix_reload_hint");
            }
        }
        sendReport(sender, result.report());
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

    private void sendPipelineOutcome(CommandSender sender, PipelineOutcome outcome, Throwable throwable) {
        if (throwable != null) {
            sendLang(sender, "command.action_execute_failed", Map.of(
                    "stage", "-",
                    "error", Texts.toStringSafe(throwable.getMessage())
            ));
            return;
        }
        if (outcome == null) {
            sendLang(sender, "command.action_execute_failed", Map.of("stage", "-", "error", "没有返回结果."));
            return;
        }
        if (outcome.status() == PipelineOutcome.Status.FAILURE) {
            PipelineOutcome.StageResult failure = firstFailedStage(outcome);

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

    private boolean handleDebug(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            sendLang(sender, "command.no_permission_admin");
            return true;
        }
        if (args.length >= 2 && "all".equalsIgnoreCase(args[1])) {
            return handleGlobalDebug(sender, args);
        }
        if (args.length >= 2 && "action".equalsIgnoreCase(args[1])) {
            return handleDebugAction(sender, args);
        }
        if (args.length < 2 || !"loops".equalsIgnoreCase(args[1])) {
            sendHelpPage(sender, helpRoot(label), List.of("debug"));
            return true;
        }
        PipelineTaskService tasks = plugin.pipelineTaskService();
        if (tasks == null) {
            sendLang(sender, "command.action_unavailable");
            return true;
        }
        String mode = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "list";
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

    private boolean handleDebugAction(CommandSender sender, String[] args) {
        ActionDebugRunner.MessageWriter writer =
                ActionDebugRunner.MessageWriter.of(plugin.messageService(), sender);
        String[] tail = args.length > 2
                ? Arrays.copyOfRange(args, 2, args.length)
                : new String[0];
        ActionDebugRunner.Invocation invocation = ActionDebugRunner.parse(tail);
        if (invocation == null) {
            writer.send("command.action_debug_usage");
            return true;
        }
        ActionEngine engine = plugin.actionEngine();
        if (invocation.mode() == ActionDebugRunner.Mode.CHECK) {
            ActionDebugRunner.check(engine, sender, invocation.rawLine(), writer);
            return true;
        }
        if (invocation.mode() == ActionDebugRunner.Mode.REGISTRY) {
            ActionDebugRunner.registry(plugin.stageRegistry(), writer);
            return true;
        }
        Player player = sender instanceof Player resolved ? resolved : null;
        ActionDebugRunner.run(engine, plugin, player, sender, invocation.rawLine(),
                invocation.mode() == ActionDebugRunner.Mode.SHADOW, writer,
                task -> dispatchSender(sender, task));
        return true;
    }

    private boolean handleGlobalDebug(CommandSender sender, String[] args) {
        String mode = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "status";
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
            plugin.messageService().sendRaw(sender, plugin.messageService().message("command.loop_task_entry", Map.of(
                    "key", snapshot.key(),
                    "plugin", snapshot.pluginName(),
                    "index", String.valueOf(snapshot.index()),
                    "times", String.valueOf(snapshot.times()),
                    "interval_ticks", String.valueOf(snapshot.intervalTicks()),
                    "player", player)));
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sendHelpPage(sender, helpRoot(label), List.of());
    }

    private String helpRoot(String label) {
        return "/" + (label == null || label.isBlank() ? "emakicorelib" : label);
    }

    private void sendHelpPage(CommandSender sender, String root, List<String> path) {
        if (path.isEmpty()) {
            sendHelpLines(sender, root, "command.help_header",
                    "command.help_entry_reload", "command.help_entry_check",
                    "command.help_entry_action", "command.help_entry_debug", "command.help_entry_help");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(0).toLowerCase(Locale.ROOT)) {
            case "check" -> sendCheckHelp(sender, root, path);
            case "action" -> sendActionHelp(sender, root);
            case "debug" -> sendDebugHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of());
        }
    }

    private void sendCheckHelp(CommandSender sender, String root, List<String> path) {
        MessageService messageService = plugin.messageService();
        if (path.size() >= 2 && "module".equalsIgnoreCase(path.get(1))) {
            sendHelpLine(sender, messageService.message("command.help_check_module_header", Map.of("root", root)));
            List<String> moduleIds = plugin.configPrecheckService().registry().moduleIds();
            if (moduleIds.isEmpty()) {
                sendHelpLine(sender, messageService.message("command.help_value_empty"));
                sendHelpFooter(sender);
                return;
            }
            for (String moduleId : moduleIds) {
                sendValueEntry(sender, moduleId,
                        messageService.message("command.help_value_desc_check_module", Map.of("value", moduleId)),
                        root + " check " + moduleId);
            }
            sendHelpFooter(sender);
            return;
        }
        sendHelpLines(sender, root, "command.help_check_header",
                "command.help_check_entry_module", "command.help_check_entry_report", "command.help_check_entry_fix");
        sendHelpFooter(sender);
    }

    private void sendActionHelp(CommandSender sender, String root) {
        sendHelpLines(sender, root, "command.help_action_header",
                "command.help_action_entry_list", "command.help_action_entry_run", "command.help_action_entry_line");
        sendHelpFooter(sender);
    }

    private void sendDebugHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() < 2) {
            sendHelpLines(sender, root, "command.help_debug_header",
                    "command.help_debug_entry_all", "command.help_debug_entry_loops", "command.help_debug_entry_action");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(1).toLowerCase(Locale.ROOT)) {
            case "all" -> {
                sendHelpLines(sender, root, "command.help_debug_all_header",
                        "command.help_debug_all_entry_on", "command.help_debug_all_entry_off", "command.help_debug_all_entry_status");
                sendHelpFooter(sender);
            }
            case "loops" -> sendLoopsHelp(sender, root, path);
            case "action" -> sendDebugActionHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of("debug"));
        }
    }

    private void sendLoopsHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 3) {
            String tail = path.get(2).toLowerCase(Locale.ROOT);
            if ("player".equals(tail)) {
                sendPlayerHelp(sender, root, "debug loops player",
                        "command.help_loops_player_header", "command.help_value_desc_loops_player");
                return;
            }
            if ("cancel-player".equals(tail)) {
                sendPlayerHelp(sender, root, "debug loops cancel-player",
                        "command.help_loops_cancel_player_header", "command.help_value_desc_loops_cancel_player");
                return;
            }
        }
        sendHelpLines(sender, root, "command.help_debug_loops_header",
                "command.help_debug_loops_entry_list", "command.help_debug_loops_entry_player",
                "command.help_debug_loops_entry_key", "command.help_debug_loops_entry_cancel",
                "command.help_debug_loops_entry_cancel_player");
        sendHelpFooter(sender);
    }

    private void sendPlayerHelp(CommandSender sender, String root, String command, String headerKey, String descKey) {
        MessageService messageService = plugin.messageService();
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
        MessageService messageService = plugin.messageService();
        Map<String, ?> replacements = Map.of("root", root);
        for (String key : keys) {
            sendHelpLine(sender, messageService.message(key, replacements));
        }
    }

    private void sendHelpLine(CommandSender sender, String line) {
        MessageService messageService = plugin.messageService();
        messageService.sendComponent(sender, messageService.render(line));
    }

    private void sendHelpFooter(CommandSender sender) {
        sendHelpLine(sender, plugin.messageService().message("command.help_footer"));
    }

    private void complete(String rawPrefix, List<String> options, List<String> result) {
        String prefix = rawPrefix.toLowerCase(Locale.ROOT);
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
