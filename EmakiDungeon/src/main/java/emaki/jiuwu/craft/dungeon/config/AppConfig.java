package emaki.jiuwu.craft.dungeon.config;

import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public record AppConfig(
        String language,
        String version,
        boolean debugEnabled,
        int debugRetentionDays,
        int maxInstances,
        String commandsMode,
        List<String> commandsRules,
        List<String> escapeBlacklist,
        int partyMaxSize,
        String partyInviteMode,
        boolean globalChatDefault,
        String timezone,
        String bstats,
        int foliaHeldWorldsMax,
        boolean rewardboxEnabled,
        boolean scriptEnabled,
        long scriptTimeoutMs,
        boolean scriptFailOnError,
        int monitorMsptP95Ms,
        int monitorMemorySlopeMbPerMin,
        int monitorQueueBacklogSeconds,
        Map<String, List<String>> actionPipelines
) {
    static final String CURRENT_VERSION = "1.0.0";

    public static AppConfig defaults() {
        return new AppConfig(
                "zh_CN",
                CURRENT_VERSION,
                false,
                30,
                20,
                "blacklist",
                List.of(),
                List.of(),
                4,
                "public",
                false,
                "",
                "disabled",
                10,
                true,
                true,
                5000L,
                false,
                55,
                64,
                5,
                Map.of()
        );
    }

    public static AppConfig parse(YamlSection section) {
        AppConfig defaults = defaults();
        Map<String, List<String>> actionPipelines = new LinkedHashMap<>();
        YamlSection action = section.getSection("action");
        if (action != null) {
            for (String segment : action.getKeys(false)) {
                List<String> lines = new ArrayList<>();
                for (Object raw : action.getList(segment)) {
                    lines.add(String.valueOf(raw));
                }
                if (!lines.isEmpty()) {
                    actionPipelines.put(segment, List.copyOf(lines));
                }
            }
        }
        return new AppConfig(
                section.getString("language", defaults.language()),
                section.getString("version", defaults.version()),
                section.getBoolean("debug.enabled", defaults.debugEnabled()),
                section.getInt("debug.retention-days", defaults.debugRetentionDays()),
                section.getInt("limit.max-instances", defaults.maxInstances()),
                section.getString("commands.mode", defaults.commandsMode()),
                section.getStringList("commands.rules"),
                section.getStringList("escape.blacklist"),
                section.getInt("party.max-size", defaults.partyMaxSize()),
                section.getString("party.invite-mode", defaults.partyInviteMode()),
                section.getBoolean("isolation.global-chat-default", defaults.globalChatDefault()),
                section.getString("general.timezone", defaults.timezone()),
                section.getString("storage.bstats", defaults.bstats()),
                section.getInt("folia.held-worlds-max", defaults.foliaHeldWorldsMax()),
                section.getBoolean("rewardbox.enabled", defaults.rewardboxEnabled()),
                section.getBoolean("script.enabled", defaults.scriptEnabled()),
                section.getInt("script.timeout-ms", (int) defaults.scriptTimeoutMs()),
                section.getBoolean("script.fail-on-error", defaults.scriptFailOnError()),
                section.getInt("monitor.mspt-p95-ms", defaults.monitorMsptP95Ms()),
                section.getInt("monitor.memory-slope-mb-per-min", defaults.monitorMemorySlopeMbPerMin()),
                section.getInt("monitor.queue-backlog-seconds", defaults.monitorQueueBacklogSeconds()),
                Map.copyOf(actionPipelines)
        );
    }
}
