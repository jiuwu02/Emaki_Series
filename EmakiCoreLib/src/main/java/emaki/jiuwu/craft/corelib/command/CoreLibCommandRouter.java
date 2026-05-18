package emaki.jiuwu.craft.corelib.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.web.WebConsoleConfig;
import emaki.jiuwu.craft.corelib.web.WebConsoleService;

public final class CoreLibCommandRouter implements TabExecutor {

    private static final String PERMISSION_WEB = "emakicorelib.web";
    private static final String PERMISSION_RELOAD = "emakicorelib.reload";
    private static final List<String> SUB_COMMANDS = List.of("help", "web", "webconsole", "url", "link", "reload", "webdebug");
    private static final List<String> WEBDEBUG_MODES = List.of("frontend", "backend", "all");

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
        return switch (args[0].toLowerCase()) {
            case "help" -> {
                sendHelp(sender, label);
                yield true;
            }
            case "web", "webconsole", "url", "link" -> handleWebConsoleLink(sender);
            case "reload" -> handleReload(sender);
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
            String prefix = args[0].toLowerCase();
            for (String subCommand : SUB_COMMANDS) {
                if (subCommand.startsWith(prefix)) {
                    result.add(subCommand);
                }
            }
        } else if (args.length == 2 && "webdebug".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase();
            for (String mode : WEBDEBUG_MODES) {
                if (mode.startsWith(prefix)) {
                    result.add(mode);
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
        plugin.reloadActionSystem();
        sendLang(sender, "command.reload_success");
        return true;
    }

    private boolean handleWebDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            sendLang(sender, "web_debug.no_permission");
            return true;
        }
        WebConsoleService service = plugin.webConsoleService();
        if (service == null) {
            sendLang(sender, "web_debug.not_running");
            return true;
        }
        String mode = args.length >= 2 ? args[1].toLowerCase() : "all";
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

    private void sendLang(CommandSender sender, String key) {
        MessageService messageService = plugin.messageService();
        messageService.sendRaw(sender, messageService.message(key));
    }

    private void sendHelp(CommandSender sender, String label) {
        String root = "/" + (label == null || label.isBlank() ? "emakicorelib" : label);
        sendLang(sender, "command.help_header");
        sendLang(sender, "command.help_web", Map.of("root", root));
        sendLang(sender, "command.help_reload", Map.of("root", root));
        sendLang(sender, "command.help_webdebug", Map.of("root", root));
    }

    private void sendLang(CommandSender sender, String key) {
        MessageService messageService = plugin.messageService();
        messageService.sendRaw(sender, messageService.message(key));
    }

    private void sendLang(CommandSender sender, String key, Map<String, ?> replacements) {
        MessageService messageService = plugin.messageService();
        messageService.sendRaw(sender, messageService.message(key, replacements));
    }

    private void sendLine(CommandSender sender, Component component) {
        AdventureSupport.sendMessage(plugin, sender, Component.text("[EmakiCoreLib] ", NamedTextColor.DARK_AQUA)
                .append(component));
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
