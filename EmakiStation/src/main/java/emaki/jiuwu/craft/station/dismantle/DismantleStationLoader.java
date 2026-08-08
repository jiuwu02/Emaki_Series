package emaki.jiuwu.craft.station.dismantle;

import java.io.File;
import java.util.Locale;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.station.api.model.OutputRouting;

/**
 * Loads {@code stations_dismantle/*.yml} into {@link DismantleStationDefinition}s.
 *
 * <p>Dismantle stations live in their own directory so a server can run dismantling without
 * declaring a crafting station, and so the two page sets can be retuned independently.
 *
 * <p>A file without an id is skipped with a recorded issue instead of aborting the directory: one
 * bad station must not cost an administrator every other one.
 */
public final class DismantleStationLoader extends YamlDirectoryLoader<DismantleStationDefinition> {

    /**
     * Creates the loader.
     *
     * @param plugin the owning plugin
     */
    public DismantleStationLoader(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "stations_dismantle";
    }

    @Override
    protected String typeName() {
        return "dismantle_station";
    }

    @Override
    protected String idOf(DismantleStationDefinition value) {
        return value == null ? null : value.id();
    }

    @Override
    protected DismantleStationDefinition parse(File file, YamlSection configuration) {
        if (configuration == null) {
            return null;
        }
        String id = normalizeId(configuration.getString("id"));
        if (id == null) {
            issue("station.dismantle_station_missing_id", Map.of("file", fileName(file)));
            return null;
        }
        YamlSection output = configuration.getSection("output");
        return new DismantleStationDefinition(id,
                configuration.getString("display_name", id),
                normalizeId(configuration.getString("layout")),
                configuration.getString("permission", ""),
                OutputRouting.parse(output == null ? null : output.getString("default"),
                        OutputRouting.STORAGE_FIRST),
                ConditionBlock.fromRoot(configuration, true, false));
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
