package emaki.jiuwu.craft.level.config;

import java.util.List;

import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public record AppConfig(String version,
        String language,
        boolean releaseDefaultData,
        String primaryType,
        int defaultStartLevel,
        int defaultMaxLevel,
        int maxAutoUpgradeSteps,
        boolean keepTotalExpAtMaxLevel,
        boolean pdcEnabled,
        String pdcNamespace,
        boolean attributeEnabled,
        String attributeProviderId,
        boolean placedBlockTracking,
        boolean placedBlockExp,
        boolean lastDamagerTracking,
        int lastDamagerExpireTicks,
        boolean guiEnabled,
        String defaultGuiTemplate,
        boolean mythicEnabled,
        boolean mythicKillSources,
        boolean mythicDropsEnabled,
        List<String> mythicDropNames) {

    public static AppConfig defaults() {
        return new AppConfig("1.0.0", "zh_CN", true, "main", 1, 100, 10, true, true, "emakilevel", true, "emakilevel", true, false, true, 200, true, "level_gui", true, true, true, List.of("emakilevel_exp", "elv_exp"));
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
                section.getBoolean("pdc.enabled", defaults.pdcEnabled),
                section.getString("pdc.namespace", defaults.pdcNamespace),
                section.getBoolean("attribute.enabled", defaults.attributeEnabled),
                section.getString("attribute.provider_id", defaults.attributeProviderId),
                section.getBoolean("anti_abuse.placed_block_tracking", defaults.placedBlockTracking),
                section.getBoolean("anti_abuse.placed_block_exp", defaults.placedBlockExp),
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
}
