package emaki.jiuwu.craft.station.config;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.station.EmakiStationPlugin;
import emaki.jiuwu.craft.station.api.model.ProgressMode;

public final class AppConfigParser {

    private AppConfigParser() {
    }

    public static AppConfig parse(YamlSection section) {
        if (section == null) {
            return AppConfig.defaults();
        }
        AppConfig defaults = AppConfig.defaults();
        return new AppConfig(
                section.getString("language", defaults.language()),
                section.getString("version", AppConfig.CURRENT_VERSION),
                section.getBoolean("release_default_data", defaults.releaseDefaultData()),
                parseQueue(section.getSection("queue"), defaults.queueSettings()),
                parseLimits(section.getSection("limits"), defaults.limitSettings()),
                parseStorage(section.getSection("storage"), defaults.storageSettings()),
                parsePersistence(section.getSection("persistence"), defaults.persistenceSettings()),
                parseGui(section.getSection("gui"), defaults.guiSettings()),
                parsePurchase(section.getSection("queue"), defaults.purchaseSettings()));
    }

    private static PurchaseSettings parsePurchase(YamlSection queueSection, PurchaseSettings defaults) {
        if (queueSection == null) {
            return defaults;
        }
        YamlSection section = queueSection.getSection("purchase");
        if (section == null) {
            return defaults;
        }
        return new PurchaseSettings(
                section.getBoolean("enabled", defaults.enabled()),
                section.getString("cost_file", defaults.costFile()));
    }

    private static QueueSettings parseQueue(YamlSection section, QueueSettings defaults) {
        if (section == null) {
            return defaults;
        }
        return new QueueSettings(
                section.getInt("base_length", defaults.baseLength()),
                section.getBoolean("permission_tiers", defaults.permissionTiers()),
                section.getInt("max_length", defaults.maxLength()),
                ProgressMode.parse(section.getString("progress_mode"), defaults.progressMode()),
                section.getDouble("cancel_refund_rate", defaults.cancelRefundRate()),
                section.getInt("tick_interval", (int) defaults.tickIntervalTicks()),
                section.getDouble("speed_multiplier", defaults.speedMultiplier()));
    }

    private static LimitSettings parseLimits(YamlSection section, LimitSettings defaults) {
        if (section == null) {
            return defaults;
        }
        Object rawBatchMax = section.get("batch_multiplier_max");
        long batchMax = rawBatchMax instanceof Number number
                ? number.longValue()
                : defaults.batchMultiplierMax();
        return new LimitSettings(
                section.getInt("max_pending_claim", defaults.maxPendingClaim()),
                section.getInt("warn_material_types", defaults.warnMaterialTypes()),
                batchMax);
    }

    private static StorageSettings parseStorage(YamlSection section, StorageSettings defaults) {
        if (section == null) {
            return defaults;
        }
        return new StorageSettings(
                section.getBoolean("enabled", defaults.enabled()),
                section.getInt("batch_max_ops", defaults.batchMaxOps()));
    }

    private static PersistenceSettings parsePersistence(YamlSection section, PersistenceSettings defaults) {
        if (section == null) {
            return defaults;
        }
        return new PersistenceSettings(
                legacyAwareInt(section, "autosave_interval_seconds", "autosave_interval",
                        defaults.autosaveIntervalSeconds()),
                section.getBoolean("save_on_submit", defaults.saveOnSubmit()));
    }

    private static GuiSettings parseGui(YamlSection section, GuiSettings defaults) {
        if (section == null) {
            return defaults;
        }
        return new GuiSettings(
                section.getInt("click_throttle_ms", defaults.clickThrottleMs()),
                legacyAwareInt(section, "refresh_interval_ticks", "refresh_interval",
                        (int) defaults.refreshTicks()));
    }

    private static int legacyAwareInt(YamlSection section, String path, String legacyPath, int fallback) {
        if (section.contains(path)) {
            return section.getInt(path, fallback);
        }
        if (section.contains(legacyPath)) {
            int legacyValue = section.getInt(legacyPath, fallback);
            JavaPlugin.getPlugin(EmakiStationPlugin.class).getLogger().warning("配置键 " + legacyPath
                    + " 已更名为 " + path
                    + "，当前按旧键值 " + legacyValue
                    + " 生效，启动时会自动迁移到新键。");
            return legacyValue;
        }
        return fallback;
    }
}
