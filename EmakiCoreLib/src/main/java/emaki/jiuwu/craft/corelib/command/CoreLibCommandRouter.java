package emaki.jiuwu.craft.corelib.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionBatchResult;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.action.ActionStepResult;
import emaki.jiuwu.craft.corelib.action.loop.LoopTaskSnapshot;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckMessages;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckReport;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class CoreLibCommandRouter implements TabExecutor {

    private static final String PERMISSION_RELOAD = "emakicorelib.reload";
    private static final String PERMISSION_ADMIN = "emakicorelib.admin";
    private static final List<String> SUB_COMMANDS = List.of("help", "reload", "check", "debug", "script", "action", "actions");
    private static final List<String> SCRIPT_MODES = List.of("list", "inspect", "reload");
    private static final List<String> ACTION_MODES = List.of("list", "run");
    private static final List<String> CHECK_MODES = List.of("report", "--fix");
    private static final List<String> DEBUG_MODES = List.of("loops");
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
            case "script" -> handleScript(sender, args);
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
        } else if (args.length == 2 && "script".equalsIgnoreCase(args[0])) {
            complete(args[1], SCRIPT_MODES, result);
        } else if (args.length == 2 && isActionCommand(args[0])) {
            complete(args[1], ACTION_MODES, result);
            complete(args[1], actionIds(), result);
        } else if (args.length == 3 && isActionCommand(args[0]) && "run".equalsIgnoreCase(args[1])) {
            complete(args[2], actionIds(), result);
        } else if (args.length == 3 && "script".equalsIgnoreCase(args[0]) && "inspect".equalsIgnoreCase(args[1])) {
            Object scripts = plugin.javaScriptExtensionStatus().get("globalExtensionScripts");
            if (scripts instanceof Iterable<?> iterable) {
                for (Object script : iterable) {
                    String value = Texts.toStringSafe(script);
                    if (value.startsWith(args[2])) {
                        result.add(value);
                    }
                }
            }
        } else if (args.length == 2 && "debug".equalsIgnoreCase(args[0])) {
            complete(args[1], DEBUG_MODES, result);
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

    private boolean handleScript(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            sendLang(sender, "command.no_permission_admin");
            return true;
        }
        String mode = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "list";
        switch (mode) {
            case "reload" -> {
                if (plugin.reloadActionSystem()) {
                    sendLang(sender, "command.script_reload_success");
                } else {
                    sendLang(sender, "command.reload_failed_precheck");
                }
            }
            case "inspect" -> sendScriptInspect(sender, args.length >= 3 ? args[2] : "");
            default -> sendScriptList(sender);
        }
        return true;
    }

    private boolean handleAction(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            sendLang(sender, "command.no_permission_admin");
            return true;
        }
        if (plugin.actionRegistry() == null || plugin.actionExecutor() == null) {
            sendLang(sender, "command.action_unavailable");
            return true;
        }
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            sendActionList(sender);
            return true;
        }
        int lineStart = "run".equalsIgnoreCase(args[1]) ? 2 : 1;
        String rawLine = joinArguments(args, lineStart);
        if (Texts.isBlank(rawLine)) {
            sendLang(sender, "command.action_usage");
            return true;
        }
        Player player = sender instanceof Player resolvedPlayer ? resolvedPlayer : null;
        ActionContext context = ActionContext.create(plugin, player, "command.action", false)
                .withPlaceholders(Map.of(
                        "sender", sender.getName(),
                        "player", player == null ? "" : player.getName(),
                        "action_line", rawLine
                ))
                .withAttribute("command_sender", sender);
        sendLang(sender, "command.action_execute_started", Map.of("line", rawLine));
        plugin.actionExecutor().executeAll(context, List.of(rawLine), true).whenComplete((batch, throwable) ->
                dispatchSender(sender, () -> sendActionExecutionResult(sender, batch, throwable))
        );
        return true;
    }

    private void sendActionList(CommandSender sender) {
        Map<String, Action> actions = new LinkedHashMap<>(plugin.actionRegistry().all());
        sendLang(sender, "command.action_list_header", Map.of("count", String.valueOf(actions.size())));
        if (actions.isEmpty()) {
            sendLang(sender, "command.action_list_empty");
            return;
        }
        actions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String id = entry.getKey();
                    Action action = entry.getValue();
                    plugin.messageService().sendRaw(sender, plugin.messageService().message("command.action_list_item", Map.of(
                            "id", id,
                            "category", Texts.toStringSafe(action.category()),
                            "source", emptyAsDash(plugin.actionRegistry().sourceOf(id)),
                            "owner", emptyAsDash(plugin.actionRegistry().ownerKeyOf(id)),
                            "mode", Texts.toStringSafe(action.executionMode()),
                            "params", actionParameters(action)
                    )));
                });
    }

    private void dispatchSender(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            executionDispatcher.runEntity(plugin, player, task, () -> { });
        } else {
            executionDispatcher.runGlobal(plugin, task);
        }
    }

    private void sendActionExecutionResult(CommandSender sender, ActionBatchResult batch, Throwable throwable) {
        if (throwable != null) {
            sendLang(sender, "command.action_execute_failed", Map.of(
                    "action", "-",
                    "error", Texts.toStringSafe(throwable.getMessage())
            ));
            return;
        }
        if (batch == null || batch.steps() == null) {
            sendLang(sender, "command.action_execute_failed", Map.of("action", "-", "error", "No result returned."));
            return;
        }
        int success = 0;
        int skipped = 0;
        int failed = 0;
        for (ActionStepResult step : batch.steps()) {
            ActionResult result = step == null ? null : step.result();
            if (result == null || !result.success()) {
                failed++;
            } else if (result.skipped()) {
                skipped++;
            } else {
                success++;
            }
        }
        if (failed > 0 || !batch.success()) {
            ActionStepResult failure = batch.firstFailure();
            ActionResult result = failure == null ? null : failure.result();
            sendLang(sender, "command.action_execute_failed", Map.of(
                    "action", failure == null ? "-" : Texts.toStringSafe(failure.actionId()),
                    "error", result == null ? "Unknown failure." : Texts.toStringSafe(result.errorMessage())
            ));
            return;
        }
        sendLang(sender, "command.action_execute_success", Map.of(
                "success", String.valueOf(success),
                "skipped", String.valueOf(skipped)
        ));
    }

    private List<String> actionIds() {
        if (plugin.actionRegistry() == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(plugin.actionRegistry().all().keySet());
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    private String actionParameters(Action action) {
        if (action == null || action.parameters().isEmpty()) {
            return "-";
        }
        List<String> parts = new ArrayList<>();
        for (ActionParameter parameter : action.parameters()) {
            parts.add(parameter.name() + (parameter.required() ? "*" : ""));
        }
        return String.join(", ", parts);
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
        if (args.length < 2 || !"loops".equalsIgnoreCase(args[1])) {
            sendHelp(sender, "corelib");
            return true;
        }
        String mode = args.length >= 3 ? args[2].toLowerCase(java.util.Locale.ROOT) : "list";
        switch (mode) {
            case "list" -> sendLoopSnapshots(sender, plugin.loopActionService().snapshots());
            case "player" -> {
                Player player = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : null;
                if (player == null) {
                    sendLang(sender, "debug.command.player_not_found", Map.of("player", args.length >= 4 ? args[3] : ""));
                } else {
                    sendLoopSnapshots(sender, plugin.loopActionService().snapshotsByPlayer(player.getUniqueId()));
                }
            }
            case "key" -> sendLoopSnapshots(sender, plugin.loopActionService().snapshotsByKey(args.length >= 4 ? args[3] : ""));
            case "cancel" -> {
                String key = args.length >= 4 ? args[3] : "";
                ActionResult result = plugin.loopActionService().cancel(key, "exact", false);
                if (result.success()) {
                    sendLang(sender, "command.loop_cancelled", Map.of("key", key));
                } else {
                    plugin.messageService().sendRaw(sender, "<red>" + result.errorMessage() + "</red>");
                }
            }
            case "cancel-player" -> {
                Player player = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : null;
                if (player == null) {
                    sendLang(sender, "debug.command.player_not_found", Map.of("player", args.length >= 4 ? args[3] : ""));
                } else {
                    int count = plugin.loopActionService().cancelByPlayer(player.getUniqueId());
                    sendLang(sender, "command.loop_player_cancelled", Map.of("player", player.getName(), "count", String.valueOf(count)));
                }
            }
            default -> sendLoopSnapshots(sender, plugin.loopActionService().snapshots());
        }
        return true;
    }

    private void sendScriptList(CommandSender sender) {
        Map<String, Object> status = plugin.javaScriptExtensionStatus();
        Object scripts = status.get("globalExtensionScripts");
        Object registrations = status.get("registrations");
        int scriptCount = scripts instanceof java.util.Collection<?> collection ? collection.size() : 0;
        int registrationCount = registrations instanceof java.util.Collection<?> collection ? collection.size() : 0;
        sendLang(sender, "command.script_list_header", Map.of("scripts", String.valueOf(scriptCount), "registrations", String.valueOf(registrationCount)));
        if (scripts instanceof Iterable<?> iterable) {
            for (Object script : iterable) {
                plugin.messageService().sendRaw(sender, plugin.messageService().message("command.script_list_item", Map.of("script", Texts.toStringSafe(script))));
            }
        }
    }

    private void sendScriptInspect(CommandSender sender, String scriptPath) {
        if (Texts.isBlank(scriptPath)) {
            sendLang(sender, "command.script_inspect_usage");
            return;
        }
        Map<String, Object> status = plugin.javaScriptExtensionStatus();
        sendLang(sender, "command.script_inspect_header", Map.of("script", scriptPath));
        Object registrations = status.get("registrations");
        int count = 0;
        if (registrations instanceof Iterable<?> iterable) {
            for (Object raw : iterable) {
                if (!(raw instanceof Map<?, ?> item) || !scriptPath.equals(Texts.toStringSafe(item.get("script")))) {
                    continue;
                }
                count++;
                plugin.messageService().sendRaw(sender, plugin.messageService().message("command.script_inspect_registration", Map.of(
                        "type", Texts.toStringSafe(item.get("type")),
                        "id", Texts.toStringSafe(item.get("id")),
                        "duration", Texts.toStringSafe(item.get("durationMillis"))
                )));
            }
        }
        if (count == 0) {
            sendLang(sender, "command.script_inspect_empty");
        }
    }

    private void sendReport(CommandSender sender, ConfigPrecheckReport report) {
        if (report == null) {
            sendLang(sender, "command.check_no_report");
            return;
        }
        ConfigPrecheckMessages.sendReport(plugin.messageService(), sender, "corelib", report);
    }

    private void sendLoopSnapshots(CommandSender sender, List<LoopTaskSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            sendLang(sender, "command.loop_empty");
            return;
        }
        sendLang(sender, "command.loop_header", Map.of("count", String.valueOf(snapshots.size())));
        for (LoopTaskSnapshot snapshot : snapshots) {
            String player = snapshot.playerUuid() == null ? "-" : snapshot.playerUuid().toString();
            plugin.messageService().sendRaw(sender, "<gray>- key=<aqua>" + snapshot.key()
                    + "</aqua> template=<yellow>" + snapshot.template()
                    + "</yellow> index=<white>" + snapshot.index() + "/" + snapshot.times()
                    + "</white> async=<white>" + snapshot.async()
                    + "</white> player=<gray>" + player + "</gray></gray>");
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        String root = "/" + (label == null || label.isBlank() ? "emakicorelib" : label);
        sendLang(sender, "command.help_header");
        sendLang(sender, "command.help_reload", Map.of("root", root));
        sendLang(sender, "command.help_check", Map.of("root", root));
        sendLang(sender, "command.help_script", Map.of("root", root));
        sendLang(sender, "command.help_action", Map.of("root", root));
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
