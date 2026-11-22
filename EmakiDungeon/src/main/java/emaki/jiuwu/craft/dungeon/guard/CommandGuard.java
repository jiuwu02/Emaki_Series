package emaki.jiuwu.craft.dungeon.guard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.command.CommandMapGuard;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.config.AppConfig;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.flow.EventIngressKeys;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.EventIngress.IngressEvent;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.trigger.TriggerType;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;
import emaki.jiuwu.craft.corelib.log.LogTag;

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

    private CommandMapGuard.Handle guardHandle;

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
        if (guardHandle != null) {
            return;
        }
        try {
            CommandMapGuard guard = EmakiCoreLibApi.commandMapGuard();
            if (guard == null) {
                messages.info(LogTag.STARTUP, "console.command_map_unsupported");
                return;
            }
            guardHandle = guard.install(this::interceptDispatch);
        } catch (RuntimeException exception) {
            messages.warning(null, "console.command_map_wrap_failed", Map.of(
                    "error", String.valueOf(exception.getMessage())
            ));
        }
    }

    public void restoreCommandMap() {
        if (guardHandle == null) {
            return;
        }
        try {
            guardHandle.close();
        } catch (RuntimeException ignored) {
            FailureRecorder.recordIgnoredFailure("EmakiDungeon", "CommandGuard.java#1", ignored);
        }
        guardHandle = null;
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
        Map<String, String> context = new LinkedHashMap<>();
        context.put("player", String.valueOf(player.getUniqueId()));
        context.put("command", commandLine);
        context.put("verdict", verdict.name());
        context.put("source", source);
        instance.ingress().post(new IngressEvent(EventIngressKeys.SCRIPT_TRIGGER,
                TriggerType.onCommandDenied.name(),
                Map.of("trigger", TriggerType.onCommandDenied.name(), "ctx", context)));
    }

    private DungeonInstance instanceOf(Player player) {
        return registry.get(player.getWorld().getName());
    }
}
