package emaki.jiuwu.craft.dungeon.guard;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.config.AppConfig;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.EventIngress.IngressEvent;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;

public final class CommandGuard implements Listener {

    public enum Verdict {
        ALLOW,
        DENY_ESCAPE,
        DENY_MODE,
        BYPASS
    }

    private static final String SOURCE_PREPROCESS = "player_input";
    private static final String SOURCE_COMMAND_MAP = "plugin_dispatch";

    private final Plugin plugin;
    private final MessageService messages;
    private final DebugLogger debug;
    private final InstanceRegistry registry;
    private final Supplier<AppConfig> appConfig;
    private final Function<String, DungeonConfig> configOf;

    private CommandMap originalCommandMap;

    public CommandGuard(Plugin plugin,
            MessageService messages,
            DebugLogger debug,
            InstanceRegistry registry,
            Supplier<AppConfig> appConfig,
            Function<String, DungeonConfig> configOf) {
        this.plugin = plugin;
        this.messages = messages;
        this.debug = debug;
        this.registry = registry;
        this.appConfig = appConfig;
        this.configOf = configOf;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        DungeonInstance instance = instanceOf(player);
        if (instance == null || player.hasPermission("emakidungeon.bypass.escape")) {
            return;
        }
        String commandLine = event.getMessage();
        Verdict verdict = judge(instance, commandLine);
        if (verdict == Verdict.ALLOW || verdict == Verdict.BYPASS) {
            return;
        }
        event.setCancelled(true);
        deny(player, instance, commandLine, verdict, SOURCE_PREPROCESS);
    }

    public boolean interceptDispatch(CommandSender sender, String commandLine) {
        if (!(sender instanceof Player player)) {
            return false;
        }
        DungeonInstance instance = instanceOf(player);
        if (instance == null || player.hasPermission("emakidungeon.bypass.escape")) {
            return false;
        }
        Verdict verdict = judge(instance, commandLine);
        if (verdict == Verdict.ALLOW || verdict == Verdict.BYPASS) {
            return false;
        }
        deny(player, instance, commandLine, verdict, SOURCE_COMMAND_MAP);
        return true;
    }

    public void installCommandMap() {
        if (originalCommandMap != null) {
            return;
        }
        try {
            CommandMap commandMap = Bukkit.getServer().getCommandMap();
            InvocationHandler handler = (proxy, method, args) -> {
                if ("dispatch".equals(method.getName()) && args != null && args.length == 2
                        && args[0] instanceof CommandSender sender && args[1] instanceof String commandLine) {
                    if (interceptDispatch(sender, commandLine)) {
                        return true;
                    }
                }
                return method.invoke(commandMap, args);
            };
            CommandMap proxied = (CommandMap) Proxy.newProxyInstance(
                    CommandGuard.class.getClassLoader(),
                    new Class<?>[] {CommandMap.class},
                    handler
            );
            Method setter = Bukkit.getServer().getClass().getMethod("setCommandMap", CommandMap.class);
            setter.invoke(Bukkit.getServer(), proxied);
            originalCommandMap = commandMap;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            messages.warning("console.command_map_wrap_failed", Map.of(
                    "error", String.valueOf(exception.getMessage())
            ));
        }
    }

    public void restoreCommandMap() {
        if (originalCommandMap == null) {
            return;
        }
        try {
            Method setter = Bukkit.getServer().getClass().getMethod("setCommandMap", CommandMap.class);
            setter.invoke(Bukkit.getServer(), originalCommandMap);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        originalCommandMap = null;
    }

    public Verdict judge(DungeonInstance instance, String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return Verdict.ALLOW;
        }
        String trimmed = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            return Verdict.ALLOW;
        }
        String root = tokens[0];
        String[] args = new String[tokens.length - 1];
        System.arraycopy(tokens, 1, args, 0, args.length);
        AppConfig config = appConfig.get();
        List<CommandRules.Pattern> escape = CommandRules.parse(config.escapeBlacklist());
        if (CommandRules.matchesAny(escape, root, args)) {
            return Verdict.DENY_ESCAPE;
        }
        DungeonConfig dungeonConfig = configOf.apply(instance.dungeonId());
        if (dungeonConfig != null) {
            List<CommandRules.Pattern> allow = CommandRules.parse(dungeonConfig.commandsAllow());
            if (CommandRules.matchesAny(allow, root, args)) {
                return Verdict.ALLOW;
            }
            List<CommandRules.Pattern> rules = CommandRules.parse(config.commandsRules());
            boolean hit = CommandRules.matchesAny(rules, root, args);
            if ("whitelist".equals(config.commandsMode())) {
                return hit ? Verdict.ALLOW : Verdict.DENY_MODE;
            }
            if (hit) {
                return Verdict.DENY_MODE;
            }
        }
        return Verdict.ALLOW;
    }

    private void deny(Player player, DungeonInstance instance, String commandLine, Verdict verdict, String source) {
        messages.send(player, verdict == Verdict.DENY_ESCAPE
                ? "command.denied_escape"
                : "command.denied_mode", Map.of("command", commandLine));
        debug.log("dungeon", player, "command_denied", Map.of(
                "command", commandLine,
                "source", source
        ));
        instance.ingress().post(new IngressEvent("onCommandDenied", String.valueOf(player.getUniqueId()), Map.of(
                "command", commandLine,
                "verdict", verdict.name(),
                "source", source
        )));
    }

    private DungeonInstance instanceOf(Player player) {
        return registry.get(player.getWorld().getName());
    }
}
