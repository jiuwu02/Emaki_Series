package emaki.jiuwu.craft.forge.loader;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;

import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;

public final class MaterialLoader extends YamlDirectoryLoader<ForgeMaterial> {

    public MaterialLoader(EmakiForgePlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "materials";
    }

    @Override
    protected String typeName() {
        return "material";
    }

    @Override
    protected ForgeMaterial parse(File file, YamlConfiguration configuration) {
        return ForgeMaterial.fromConfig(configuration);
    }

    public ForgeMaterial parseDocument(File file, YamlConfiguration configuration) {
        return parse(file, configuration);
    }

    @Override
    protected String idOf(ForgeMaterial value) {
        return value.id();
    }
}
