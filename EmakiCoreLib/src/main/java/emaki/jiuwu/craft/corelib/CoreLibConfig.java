package emaki.jiuwu.craft.corelib;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public record CoreLibConfig(
        String language,
        boolean releaseDefaultData,
        Map<String, List<String>> actionTemplates,
        LoopConfig loopConfig,
        ScriptConfig scriptConfig,
        GuiConfig guiConfig,
        GameplayEventConfig gameplayEventConfig,
        DebugConfig debugConfig,
        MiniMessageConfig miniMessageConfig,
        DialogConfig dialogConfig,
        DisplayConfig displayConfig
) {

    public static CoreLibConfig defaults() {
        return new CoreLibConfig("zh_CN", true, Map.of(), LoopConfig.defaults(), ScriptConfig.defaults(),
                GuiConfig.defaults(), GameplayEventConfig.defaults(), DebugConfig.defaults(),
                MiniMessageConfig.defaults(), DialogConfig.defaults(), DisplayConfig.defaults());
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
                configuration.getBoolean("release_default_data", defaults().releaseDefaultData()),
                Map.copyOf(templates),
                LoopConfig.fromConfig(actionSection == null ? null : actionSection.getSection("loop")),
                ScriptConfig.fromConfig(configuration.getSection("script")),
                GuiConfig.fromConfig(configuration.getSection("gui")),
                GameplayEventConfig.fromConfig(configuration.getSection("gameplay_events")),
                DebugConfig.fromConfig(configuration.getSection("debug")),
                MiniMessageConfig.fromConfig(configuration.getSection("minimessage")),
                DialogConfig.fromConfig(configuration.getSection("dialog")),
                DisplayConfig.fromConfig(configuration.getSection("display"))
        );
    }













    public record GuiConfig(String backend, int clickIntervalMs) {

        public GuiConfig {
            backend = backend == null ? "bukkit" : backend.trim().toLowerCase(java.util.Locale.ROOT);
            clickIntervalMs = Math.max(0, clickIntervalMs);
        }

        public static GuiConfig defaults() {
            return new GuiConfig("bukkit", 100);
        }

        public static GuiConfig fromConfig(YamlSection section) {
            if (section == null) {
                return defaults();
            }
            return new GuiConfig(
                    section.getString("backend", defaults().backend()),
                    section.getInt("click_interval_ms", defaults().clickIntervalMs())
            );
        }
    }

    public record MiniMessageConfig(boolean defaultNoItalic) {

        public static MiniMessageConfig defaults() {
            return new MiniMessageConfig(true);
        }

        public static MiniMessageConfig fromConfig(YamlSection section) {
            if (section == null) {
                return defaults();
            }
            return new MiniMessageConfig(
                    section.getBoolean("default_no_italic", defaults().defaultNoItalic())
            );
        }
    }

    /**
     * 展示实体后端设置。
     *
     * <p>{@code backend} 取 {@code inherit} 时跟随 {@link GuiConfig#backend()}，
     * 由装配处调用 {@link #resolveBackend(String)} 解析。
     */
    public record DisplayConfig(String backend, int viewDistanceBlocks, int refreshIntervalTicks) {

        public static final String INHERIT = "inherit";

        public DisplayConfig {
            backend = backend == null || backend.isBlank()
                    ? "auto"
                    : backend.trim().toLowerCase(java.util.Locale.ROOT);
            viewDistanceBlocks = Math.max(1, viewDistanceBlocks);
            refreshIntervalTicks = Math.max(1, refreshIntervalTicks);
        }

        public static DisplayConfig defaults() {
            return new DisplayConfig("auto", 48, 20);
        }

        public static DisplayConfig fromConfig(YamlSection section) {
            if (section == null) {
                return defaults();
            }
            return new DisplayConfig(
                    section.getString("backend", defaults().backend()),
                    section.getInt("view_distance_blocks", defaults().viewDistanceBlocks()),
                    section.getInt("refresh_interval_ticks", defaults().refreshIntervalTicks())
            );
        }

        /**
         * 解析实际生效的后端名。
         *
         * @param guiBackend 菜单后端名，供 {@code inherit} 回落
         * @return 生效的后端名
         */
        public String resolveBackend(String guiBackend) {
            if (!INHERIT.equals(backend)) {
                return backend;
            }
            return guiBackend == null || guiBackend.isBlank() ? "auto" : guiBackend;
        }
    }

    public record DialogConfig(boolean enabled, String directory) {

        public DialogConfig {
            directory = directory == null || directory.isBlank() ? "dialogs" : directory.trim();
        }

        public static DialogConfig defaults() {
            return new DialogConfig(true, "dialogs");
        }

        public static DialogConfig fromConfig(YamlSection section) {
            if (section == null) {
                return defaults();
            }
            return new DialogConfig(
                    section.getBoolean("enabled", defaults().enabled()),
                    section.getString("directory", defaults().directory())
            );
        }
    }

















    public record DebugConfig(boolean globalAll) {

        public static DebugConfig defaults() {
            return new DebugConfig(false);
        }

        public static DebugConfig fromConfig(YamlSection section) {
            if (section == null) {
                return defaults();
            }
            return new DebugConfig(section.getBoolean("global_all", defaults().globalAll()));
        }
    }

    public record GameplayEventConfig(
            boolean enabled,
            int lastDamagerExpireTicks,
            long brewAttributionExpireTicks
    ) {

        public static GameplayEventConfig defaults() {
            return new GameplayEventConfig(true, 200, 6000L);
        }

        public static GameplayEventConfig fromConfig(YamlSection section) {
            GameplayEventConfig defaults = defaults();
            if (section == null) {
                return defaults;
            }
            Boolean enabled = section.getBoolean("enabled", defaults.enabled());
            Integer lastDamager = section.getInt("last_damager_expire_ticks", defaults.lastDamagerExpireTicks());
            Integer brew = section.getInt("brew_attribution_expire_ticks", (int) defaults.brewAttributionExpireTicks());
            return new GameplayEventConfig(
                    enabled == null ? defaults.enabled() : enabled,
                    lastDamager == null ? defaults.lastDamagerExpireTicks() : Math.max(0, lastDamager),
                    brew == null ? defaults.brewAttributionExpireTicks() : Math.max(0L, brew.longValue())
            );
        }
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
