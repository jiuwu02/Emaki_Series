package emaki.jiuwu.craft.corelib.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.web.WebConsoleConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class CoreLibCommandRouter implements TabExecutor {

    private static final String PERMISSION_WEB = "emakicorelib.web";
    private static final String PERMISSION_RELOAD = "emakicorelib.reload";
    private static final List<String> SUB_COMMANDS = List.of("help", "web", "webconsole", "url", "link", "reload");

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
        }
        return result;
    }

    private boolean handleWebConsoleLink(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_WEB)) {
            sendLine(sender, Component.text("你没有权限查看 Web Console 链接。", NamedTextColor.RED));
            return true;
        }
        WebConsoleConfig config = plugin.configModel().webConsoleConfig();
        String configuredUrl = webConsoleUrl(config.host(), config.port());
        String clickableUrl = clickableUrl(config.host(), config.port());
        sendLine(sender, Component.text("Web Console 地址：", NamedTextColor.AQUA)
                .append(Component.text(configuredUrl, NamedTextColor.WHITE)));
        if (!configuredUrl.equals(clickableUrl)) {
            sendLine(sender, Component.text("当前 host 是绑定地址，客户端通常不能直接访问；下面提供本机回退链接。", NamedTextColor.YELLOW));
        }
        sendLine(sender, Component.text("点击打开：", NamedTextColor.AQUA)
                .append(Component.text(clickableUrl, NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(clickableUrl))
                        .hoverEvent(HoverEvent.showText(Component.text("点击在浏览器中打开 Web Console", NamedTextColor.YELLOW)))));
        if (!config.enabled()) {
            sendLine(sender, Component.text("提示：web_console.enabled 当前为 false，链接可能无法连接。", NamedTextColor.YELLOW));
        }
        if (config.hasUnsafeDefaultPassword()) {
            sendLine(sender, Component.text("提示：Web Console 密码为空或仍为默认值，服务会拒绝启动。请修改 web_console.auth.password。", NamedTextColor.YELLOW));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            sendLine(sender, Component.text("你没有权限重载 EmakiCoreLib。", NamedTextColor.RED));
            return true;
        }
        plugin.reloadActionSystem();
        sendLine(sender, Component.text("EmakiCoreLib 已重载，Web Console 已按新配置重新启动。", NamedTextColor.GREEN));
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        String root = "/" + (label == null || label.isBlank() ? "emakicorelib" : label);
        sendLine(sender, Component.text("EmakiCoreLib 命令：", NamedTextColor.AQUA));
        sendLine(sender, Component.text(root + " web", NamedTextColor.GREEN)
                .append(Component.text(" - 输出可点击的 Web Console 链接", NamedTextColor.GRAY)));
        sendLine(sender, Component.text(root + " reload", NamedTextColor.GREEN)
                .append(Component.text(" - 重载 CoreLib 配置与 Web Console", NamedTextColor.GRAY)));
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
