package emaki.jiuwu.craft.strengthen.loader;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;

import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenMaterial;

public final class StrengthenMaterialLoader extends YamlDirectoryLoader<StrengthenMaterial> {

    public StrengthenMaterialLoader(EmakiStrengthenPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "materials";
    }

    @Override
    protected String typeName() {
        return "strengthen-material";
    }

    @Override
    protected StrengthenMaterial parse(File file, YamlConfiguration configuration) {
        return StrengthenMaterial.fromConfig(configuration);
    }

    @Override
    protected String idOf(StrengthenMaterial value) {
        return value.id();
    }
}
