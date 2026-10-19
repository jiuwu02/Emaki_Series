package emaki.jiuwu.craft.dungeon.config;

import java.util.List;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

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
        int foliaHeldWorldsMax
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
                10
        );
    }

    public static AppConfig parse(YamlSection section) {
        AppConfig defaults = defaults();
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
                section.getInt("folia.held-worlds-max", defaults.foliaHeldWorldsMax())
        );
    }
}
