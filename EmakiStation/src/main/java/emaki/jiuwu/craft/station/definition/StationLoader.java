package emaki.jiuwu.craft.station.definition;

import java.io.File;
import java.util.Locale;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.api.model.OutputRouting;
import emaki.jiuwu.craft.station.api.model.ProgressMode;
import emaki.jiuwu.craft.station.config.QueueSettings;

/**
 * Loads {@code stations/*.yml} into {@link StationDefinition}s.
 *
 * <p>Every station inherits the global queue defaults and overrides only the keys it declares, so an
 * administrator can retune all stations from {@code config.yml} without editing each file.
 *
 * <p>A station with both material channels disabled is rejected: it could be opened but never used, and
 * failing at load time with a clear reason is more useful than an inert GUI.
 */
public final class StationLoader extends YamlDirectoryLoader<StationDefinition> {

    private final QueueSettings globalQueueDefaults;

    /**
     * Creates the loader.
     *
     * @param plugin              the owning plugin
     * @param globalQueueDefaults the queue defaults from {@code config.yml}
     */
    public StationLoader(JavaPlugin plugin, QueueSettings globalQueueDefaults) {
        super(plugin);
        this.globalQueueDefaults = globalQueueDefaults == null
                ? QueueSettings.defaults()
                : globalQueueDefaults.normalized();
    }

    @Override
    protected String directoryName() {
        return "stations";
    }

    @Override
    protected String typeName() {
        return "station";
    }

    @Override
    protected String idOf(StationDefinition value) {
        return value == null ? null : value.id();
    }

    @Override
    protected StationDefinition parse(File file, YamlSection configuration) {
        if (configuration == null) {
            return null;
        }
        String id = normalizeId(configuration.getString("id"));
        if (id == null) {
            issue("station.station_missing_id", Map.of("file", fileName(file)));
            return null;
        }
        YamlSection channels = configuration.getSection("channels");
        boolean backpack = channels == null || channels.getBoolean("backpack", Boolean.TRUE);
        boolean storage = channels == null || channels.getBoolean("storage", Boolean.TRUE);
        if (!backpack && !storage) {
            issue("station.station_no_channel", Map.of("station", id, "file", fileName(file)));
            return null;
        }
        YamlSection output = configuration.getSection("output");
        YamlSection queue = configuration.getSection("queue");
        return new StationDefinition(id,
                configuration.getString("display_name", id),
                normalizeId(configuration.getString("layout")),
                normalizeId(configuration.getString("preview_layout")),
                normalizeId(configuration.getString("queue_layout")),
                configuration.getString("permission", ""),
                parseQueue(queue),
                queue != null && queue.getBoolean("allow_purchase", Boolean.FALSE),
                backpack,
                storage,
                MaterialChannel.parse(channels == null ? null : channels.getString("default"),
                        MaterialChannel.BACKPACK),
                OutputRouting.parse(output == null ? null : output.getString("default"),
                        OutputRouting.STORAGE_FIRST),
                output == null || output.getBoolean("player_switchable", Boolean.TRUE),
                ConditionBlock.fromRoot(configuration, true, false));
    }

    private QueueSettings parseQueue(YamlSection queue) {
        if (queue == null) {
            return globalQueueDefaults;
        }
        return new QueueSettings(
                queue.getInt("base_length", globalQueueDefaults.baseLength()),
                queue.getBoolean("permission_tiers", globalQueueDefaults.permissionTiers()),
                queue.getInt("max_length", globalQueueDefaults.maxLength()),
                ProgressMode.parse(queue.getString("progress_mode"), globalQueueDefaults.progressMode()),
                queue.getDouble("cancel_refund_rate", globalQueueDefaults.cancelRefundRate()),
                globalQueueDefaults.tickIntervalTicks(),
                queue.getDouble("speed_multiplier", globalQueueDefaults.speedMultiplier()));
    }

    private static String normalizeId(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String fileName(File file) {
        return file == null ? "?" : file.getName();
    }
}
