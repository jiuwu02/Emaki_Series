package emaki.jiuwu.craft.strengthen.loader;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;

import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenProfile;

public final class StrengthenProfileLoader extends YamlDirectoryLoader<StrengthenProfile> {

    public StrengthenProfileLoader(EmakiStrengthenPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "profiles";
    }

    @Override
    protected String typeName() {
        return "strengthen-profile";
    }

    @Override
    protected StrengthenProfile parse(File file, YamlConfiguration configuration) {
        return StrengthenProfile.fromConfig(configuration);
    }

    @Override
    protected String idOf(StrengthenProfile value) {
        return value.id();
    }
}
