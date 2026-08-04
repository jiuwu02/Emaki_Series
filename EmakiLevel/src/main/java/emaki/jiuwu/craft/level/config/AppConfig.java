package emaki.jiuwu.craft.level.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public record AppConfig(String version,
        String language,
        boolean releaseDefaultData,
        String primaryType,
        int defaultStartLevel,
        int defaultMaxLevel,
        int maxAutoUpgradeSteps,
        boolean keepTotalExpAtMaxLevel,
        ExperienceMultiplierConfig experienceMultipliers,
        DailyLimitConfig dailyLimits,
        boolean pdcEnabled,
        String pdcNamespace,
        boolean attributeEnabled,
        String attributeProviderId,
        boolean placedBlockTracking,
        boolean placedBlockExp,
        int placedBlockRecordTtlTicks,
        boolean lastDamagerTracking,
        int lastDamagerExpireTicks,
        boolean guiEnabled,
        String defaultGuiTemplate,
        boolean mythicEnabled,
        boolean mythicKillSources,
        boolean mythicDropsEnabled,
        List<String> mythicDropNames) {

    public static AppConfig defaults() {
        return new AppConfig(
                "1.2.8",
                "zh_CN",
                true,
                "main",
                1,
                100,
                10,
                true,
                ExperienceMultiplierConfig.defaults(),
                DailyLimitConfig.defaults(),
                true,
                "emakilevel",
                true,
                "emakilevel",
                true,
                false,
                864000,
                true,
                200,
                true,
                "level_gui",
                true,
                true,
                true,
                List.of("emakilevel_exp", "elv_exp")
        );
    }

    public static AppConfig parse(YamlSection section) {
        AppConfig defaults = defaults();
        if (section == null || section.isEmpty()) {
            return defaults;
        }
        List<String> mythicDropNames = section.getStringList("mythicmobs.drops.names");
        if (mythicDropNames.isEmpty()) {
            mythicDropNames = defaults.mythicDropNames;
        }
        return new AppConfig(
                section.getString("version", defaults.version),
                section.getString("language", defaults.language),
                section.getBoolean("release_default_data", defaults.releaseDefaultData),
                section.getString("primary_type", defaults.primaryType),
                section.getInt("level.default_start_level", defaults.defaultStartLevel),
                section.getInt("level.default_max_level", defaults.defaultMaxLevel),
                section.getInt("level.max_auto_upgrade_steps", defaults.maxAutoUpgradeSteps),
                section.getBoolean("level.keep_total_exp_at_max_level", defaults.keepTotalExpAtMaxLevel),
                ExperienceMultiplierConfig.parse(section.getSection("multipliers"), defaults.experienceMultipliers),
                DailyLimitConfig.parse(section.getSection("daily_caps"), defaults.dailyLimits),
                section.getBoolean("pdc.enabled", defaults.pdcEnabled),
                section.getString("pdc.namespace", defaults.pdcNamespace),
                section.getBoolean("attribute.enabled", defaults.attributeEnabled),
                section.getString("attribute.provider_id", defaults.attributeProviderId),
                section.getBoolean("anti_abuse.placed_block_tracking", defaults.placedBlockTracking),
                section.getBoolean("anti_abuse.placed_block_exp", defaults.placedBlockExp),
                section.getInt("anti_abuse.placed_block_record_ttl_ticks", defaults.placedBlockRecordTtlTicks),
                section.getBoolean("anti_abuse.last_damager_tracking.enabled", defaults.lastDamagerTracking),
                section.getInt("anti_abuse.last_damager_tracking.expire_ticks", defaults.lastDamagerExpireTicks),
                section.getBoolean("gui.enabled", defaults.guiEnabled),
                section.getString("gui.default_template", defaults.defaultGuiTemplate),
                section.getBoolean("mythicmobs.enabled", defaults.mythicEnabled),
                section.getBoolean("mythicmobs.kill_sources", defaults.mythicKillSources),
                section.getBoolean("mythicmobs.drops.enabled", defaults.mythicDropsEnabled),
                mythicDropNames
        );
    }

    public record ExperienceMultiplierConfig(boolean enabled, double global, Map<String, Double> types, Map<String, Double> reasons) {

        public ExperienceMultiplierConfig {
            types = types == null ? Map.of() : Map.copyOf(types);
            reasons = reasons == null ? Map.of() : Map.copyOf(reasons);
        }

        public static ExperienceMultiplierConfig defaults() {
            return new ExperienceMultiplierConfig(true, 1D, Map.of(), Map.of());
        }

        static ExperienceMultiplierConfig parse(YamlSection section, ExperienceMultiplierConfig defaults) {
            if (section == null) {
                return defaults;
            }
            return new ExperienceMultiplierConfig(
                    section.getBoolean("enabled", defaults.enabled),
                    section.getDouble("global", defaults.global),
                    parseDoubleMap(section.getSection("types"), defaults.types),
                    parseDoubleMap(section.getSection("reasons"), defaults.reasons)
            );
        }
    }

    public record DailyLimitConfig(boolean enabled, double defaultLimit, Map<String, Double> types) {

        public DailyLimitConfig {
            types = types == null ? Map.of() : Map.copyOf(types);
        }

        public static DailyLimitConfig defaults() {
            return new DailyLimitConfig(true, -1D, Map.of());
        }

        static DailyLimitConfig parse(YamlSection section, DailyLimitConfig defaults) {
            if (section == null) {
                return defaults;
            }
            return new DailyLimitConfig(
                    section.getBoolean("enabled", defaults.enabled),
                    section.getDouble("default_limit", defaults.defaultLimit),
                    parseDoubleMap(section.getSection("types"), defaults.types)
            );
        }
    }

    private static Map<String, Double> parseDoubleMap(YamlSection section, Map<String, Double> fallback) {
        if (section == null || section.isEmpty()) {
            return fallback == null ? Map.of() : fallback;
        }
        Map<String, Double> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            values.put(key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT), section.getDouble(key, 0D));
        }
        return values;
    }
}
