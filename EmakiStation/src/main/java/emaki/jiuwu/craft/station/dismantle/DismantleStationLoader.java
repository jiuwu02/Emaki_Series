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

public final class DismantleStationLoader extends YamlDirectoryLoader<DismantleStationDefinition> {

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
