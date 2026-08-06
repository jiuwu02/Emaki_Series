package emaki.jiuwu.craft.station.config;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.station.api.model.ProgressMode;

/**
 * Turns a raw {@code config.yml} section into an {@link AppConfig}.
 *
 * <p>Every read falls back to the shipped default rather than failing, so a partially hand-edited file
 * still yields a usable runtime. Range clamping is left to each settings record's {@code normalized()},
 * which {@link AppConfig}'s constructor applies.
 */
public final class AppConfigParser {

    private AppConfigParser() {
    }

    /**
     * Parses a configuration section.
     *
     * @param section the root section; {@code null} yields the shipped defaults
     * @return the parsed configuration
     */
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

    /**
     * Reads the {@code queue.purchase} block.
     *
     * <p>Nested under {@code queue} rather than given its own top-level section because it configures the
     * same feature the surrounding block does, and a station opts in through its own {@code queue} block.
     *
     * @param queueSection the {@code queue} section; {@code null} yields the defaults
     * @param defaults     the shipped defaults
     * @return the parsed settings
     */
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
                section.getInt("autosave_interval", defaults.autosaveIntervalSeconds()),
                section.getBoolean("save_on_submit", defaults.saveOnSubmit()));
    }

    private static GuiSettings parseGui(YamlSection section, GuiSettings defaults) {
        if (section == null) {
            return defaults;
        }
        return new GuiSettings(
                section.getInt("click_throttle_ms", defaults.clickThrottleMs()),
                section.getInt("refresh_interval", (int) defaults.refreshTicks()));
    }
}
