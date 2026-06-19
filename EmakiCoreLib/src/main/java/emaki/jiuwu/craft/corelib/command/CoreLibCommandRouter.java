package emaki.jiuwu.craft.corelib.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.action.loop.LoopTaskSnapshot;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckMessages;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckReport;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.web.WebConsoleConfig;
import emaki.jiuwu.craft.corelib.web.WebConsoleService;

public final class CoreLibCommandRouter implements TabExecutor {

    private static final String PERMISSION_WEB = "emakicorelib.web";
    private static final String PERMISSION_RELOAD = "emakicorelib.reload";
    private static final String PERMISSION_ADMIN = "emakicorelib.admin";
    private static final List<String> SUB_COMMANDS = List.of("help", "web", "webconsole", "url", "link", "reload", "check", "debug", "webdebug");
    private static final List<String> WEBDEBUG_MODES = List.of("frontend", "backend", "all");
    private static final List<String> CHECK_MODES = List.of("report", "--fix");
    private static final List<String> DEBUG_MODES = List.of("loops");
    private static final List<String> LOOP_DEBUG_MODES = List.of("list", "player", "key", "cancel", "cancel-player");

    private final EmakiCoreLibPlugin plugin;

    public CoreLibCommandRouter(EmakiCoreLibPlugin plugin) {
        this.plugin = plugin;
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
            case "web", "webconsole", "url", "link" -> handleWebConsoleLink(sender);
            case "reload" -> handleReload(sender);
            case "check" -> handleCheck(sender, args);
            case "debug" -> handleDebug(sender, args);
            case "webdebug" -> handleWebDebug(sender, args);
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
        } else if (args.length == 2 && "webdebug".equalsIgnoreCase(args[0])) {
            complete(args[1], WEBDEBUG_MODES, result);
        } else if (args.length == 2 && "check".equalsIgnoreCase(args[0])) {
            complete(args[1], CHECK_MODES, result);
            complete(args[1], plugin.configPrecheckService().registry().moduleIds(), result);
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

    private boolean handleWebConsoleLink(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_WEB)) {
            sendLang(sender, "command.no_permission_web");
            return true;
        }
        WebConsoleConfig config = plugin.configModel().webConsoleConfig();
        String configuredUrl = webConsoleUrl(config.host(), config.port());
        String clickableUrl = clickableUrl(config.host(), config.port());
        sendLang(sender, "command.web_address", Map.of("url", configuredUrl));
        if (!configuredUrl.equals(clickableUrl)) {
            sendLang(sender, "command.web_bind_hint");
        }
        sendLang(sender, "command.web_click_open", Map.of("url", clickableUrl));
        if (!config.enabled()) {
            sendLang(sender, "command.web_disabled_hint");
        }
        if (config.hasUnsafeDefaultPassword()) {
            sendLang(sender, "command.web_unsafe_password");
        }
        return true;
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

    private boolean handleWebDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            sendLang(sender, "web_debug.no_permission");
            return true;
        }
        WebConsoleConfig config = plugin.configModel().webConsoleConfig();
        if (config == null || !config.enabled()) {
            sendLang(sender, "web_debug.disabled_config");
            return true;
        }
        WebConsoleService service = plugin.webConsoleService();
        if (service == null) {
            sendLang(sender, "web_debug.not_running");
            return true;
        }
        String mode = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "all";
        switch (mode) {
            case "frontend" -> {
                boolean enabled = service.toggleDebugFrontend();
                sendLang(sender, enabled ? "web_debug.enabled_frontend" : "web_debug.disabled_frontend");
            }
            case "backend" -> {
                boolean enabled = service.toggleDebugBackend();
                sendLang(sender, enabled ? "web_debug.enabled_backend" : "web_debug.disabled_backend");
            }
            default -> {
                boolean enabled = service.toggleDebug();
                sendLang(sender, enabled ? "web_debug.enabled_all" : "web_debug.disabled_all");
            }
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
        sendLang(sender, "command.help_web", Map.of("root", root));
        sendLang(sender, "command.help_reload", Map.of("root", root));
        sendLang(sender, "command.help_check", Map.of("root", root));
        sendLang(sender, "command.help_debug_loops", Map.of("root", root));
        sendLang(sender, "command.help_webdebug", Map.of("root", root));
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

    private String clickableUrl(String host, int port) {
        String normalizedHost = normalizeHost(host);
        if ("0.0.0.0".equals(normalizedHost) || "::".equals(normalizedHost) || "[::]".equals(normalizedHost)) {
            normalizedHost = "127.0.0.1";
        }
        return webConsoleUrl(normalizedHost, port);
    }

    private String webConsoleUrl(String host, int port) {
        String normalizedHost = normalizeHost(host);
        if (normalizedHost.contains(":") && !normalizedHost.startsWith("[")) {
            normalizedHost = "[" + normalizedHost + "]";
        }
        return "http://" + normalizedHost + ":" + port + "/";
    }

    private String normalizeHost(String host) {
        String normalized = Texts.toStringSafe(host).trim();
        return normalized.isEmpty() ? "127.0.0.1" : normalized;
    }
}
