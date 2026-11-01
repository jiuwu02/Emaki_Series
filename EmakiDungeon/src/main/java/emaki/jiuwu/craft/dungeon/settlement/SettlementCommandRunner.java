package emaki.jiuwu.craft.dungeon.settlement;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SettlementCommandRunner {

    public record RunResult(boolean success, int steps) {
    }

    private static final int MAX_ATTEMPTS = 3;

    private final DebugLogger debug;

    public SettlementCommandRunner(DebugLogger debug) {
        this.debug = debug;
    }

    public RunResult run(List<String> commands, List<UUID> members, String instanceId) {
        if (commands == null || commands.isEmpty()) {
            return new RunResult(true, 0);
        }
        int steps = 0;
        boolean allSucceeded = true;
        for (String template : commands) {
            for (UUID memberId : members) {
                Player player = Bukkit.getPlayer(memberId);
                if (player == null) {
                    continue;
                }
                String commandLine = template.replace("%player%", player.getName());
                boolean executed = false;
                for (int attempt = 0; attempt < MAX_ATTEMPTS && !executed; attempt++) {
                    try {
                        executed = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandLine);
                    } catch (RuntimeException exception) {
                        debug.log("dungeon", (UUID) null, "settle_step_failed", mapOf(
                                "command", commandLine,
                                "error", String.valueOf(exception.getMessage()),
                                "instance", instanceId
                        ));
                    }
                }
                steps++;
                if (!executed) {
                    allSucceeded = false;
                    debug.log("dungeon", (UUID) null, "settle_step_skipped", mapOf(
                            "command", commandLine,
                            "player", player.getName(),
                            "instance", instanceId
                    ));
                }
            }
        }
        return new RunResult(allSucceeded, steps);
    }

    private static Map<String, String> mapOf(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
