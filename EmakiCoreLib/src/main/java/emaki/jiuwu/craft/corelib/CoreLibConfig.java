package emaki.jiuwu.craft.corelib;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.web.WebConsoleConfig;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public record CoreLibConfig(
        String language,
        Map<String, List<String>> actionTemplates,
        LoopConfig loopConfig,
        ScriptConfig scriptConfig,
        WebConsoleConfig webConsoleConfig
) {

    public static CoreLibConfig defaults() {
        return new CoreLibConfig("zh_CN", Map.of(), LoopConfig.defaults(), ScriptConfig.defaults(), WebConsoleConfig.defaults());
    }

    public static CoreLibConfig fromConfig(YamlSection configuration) {
        if (configuration == null) {
            return defaults();
        }
        String language = configuration.getString("language", defaults().language());
        YamlSection actionSection = configuration.getSection("action");
        YamlSection templatesSection = actionSection == null ? null : actionSection.getSection("templates");
        Map<String, List<String>> templates = new LinkedHashMap<>();
        if (templatesSection != null) {
            for (String key : templatesSection.getKeys(false)) {
                templates.put(key, List.copyOf(templatesSection.getStringList(key)));
            }
        }
        return new CoreLibConfig(
                language,
                Map.copyOf(templates),
                LoopConfig.fromConfig(actionSection == null ? null : actionSection.getSection("loop")),
                ScriptConfig.fromConfig(configuration.getSection("script")),
                WebConsoleConfig.fromConfig(configuration.getSection("web_console"))
        );
    }

    public record LoopConfig(
            boolean enabled,
            long minSyncIntervalTicks,
            long minAsyncIntervalTicks,
            int maxTimes,
            int maxActiveLoopsTotal,
            int maxActiveLoopsPerPlayer,
            int maxActiveLoopsPerPlugin,
            boolean cancelPlayerLoopsOnQuit,
            boolean cancelPluginLoopsOnDisable
    ) {

        public static LoopConfig defaults() {
            return new LoopConfig(true, 5L, 2L, 7200, 5000, 16, 1000, true, true);
        }

        public static LoopConfig fromConfig(YamlSection section) {
            LoopConfig defaults = defaults();
            if (section == null) {
                return defaults;
            }
            return new LoopConfig(
                    section.getBoolean("enabled", defaults.enabled()),
                    parseTicks(section.getString("min_sync_interval", "5t"), defaults.minSyncIntervalTicks()),
                    parseTicks(section.getString("min_async_interval", "100ms"), defaults.minAsyncIntervalTicks()),
                    section.getInt("max_times", defaults.maxTimes()),
                    section.getInt("max_active_loops_total", defaults.maxActiveLoopsTotal()),
                    section.getInt("max_active_loops_per_player", defaults.maxActiveLoopsPerPlayer()),
                    section.getInt("max_active_loops_per_plugin", defaults.maxActiveLoopsPerPlugin()),
                    section.getBoolean("cancel_player_loops_on_quit", defaults.cancelPlayerLoopsOnQuit()),
                    section.getBoolean("cancel_plugin_loops_on_disable", defaults.cancelPluginLoopsOnDisable())
            );
        }

        private static long parseTicks(String raw, long fallback) {
            long parsed = emaki.jiuwu.craft.corelib.action.ActionParsers.parseTicks(raw);
            return parsed < 0L ? fallback : parsed;
        }
    }
}
