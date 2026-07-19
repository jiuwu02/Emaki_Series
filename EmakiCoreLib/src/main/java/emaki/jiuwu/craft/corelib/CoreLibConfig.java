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
        GameplayEventConfig gameplayEventConfig
) {

    public static CoreLibConfig defaults() {
        return new CoreLibConfig("zh_CN", true, Map.of(), LoopConfig.defaults(), ScriptConfig.defaults(),
                GuiConfig.defaults(), GameplayEventConfig.defaults());
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
                GameplayEventConfig.fromConfig(configuration.getSection("gameplay_events"))
        );
    }

    /**
     * Selects which {@link emaki.jiuwu.craft.corelib.gui.GuiBackend} presents
     * Emaki menus.
     *
     * <ul>
     *   <li>{@code bukkit} — real server-side inventory (default).</li>
     *   <li>{@code packet} — packet-driven virtual container (requires
     *       PacketEvents); cursor survives in-place row-count changes.</li>
     *   <li>{@code auto} — packet when PacketEvents is present, otherwise
     *       bukkit.</li>
     * </ul>
     */
    public record GuiConfig(String backend) {

        public GuiConfig {
            backend = backend == null ? "bukkit" : backend.trim().toLowerCase(java.util.Locale.ROOT);
        }

        public static GuiConfig defaults() {
            return new GuiConfig("bukkit");
        }

        public static GuiConfig fromConfig(YamlSection section) {
            if (section == null) {
                return defaults();
            }
            return new GuiConfig(section.getString("backend", defaults().backend()));
        }
    }

    /**
     * Configuration for the shared gameplay-event publisher
     * ({@link emaki.jiuwu.craft.corelib.event.gameplay.GameplayEventPublisher}).
     *
     * <p>When {@code enabled}, CoreLib registers a single Bukkit listener that captures the
     * common gameplay signals (kills, block breaks, crafting, ...) and republishes them on the
     * shared event bus for every Emaki plugin. The two attribution windows are deliberately
     * coarse upper bounds: subscribers may enforce their own tighter, per-rule windows.
     *
     * <ul>
     *   <li>{@code lastDamagerExpireTicks} — how long a projectile / delayed-death attribution
     *       stays valid for {@code entity_kill} when Bukkit reports no direct killer.</li>
     *   <li>{@code brewAttributionExpireTicks} — how long the last brewing-stand user stays
     *       eligible for {@code brew_complete} attribution.</li>
     * </ul>
     */
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
