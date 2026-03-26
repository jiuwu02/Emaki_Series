package emaki.jiuwu.craft.forge.loader;

import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MaterialLoader extends AbstractDirectoryLoader<ForgeMaterial> {

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

    @Override
    protected String idOf(ForgeMaterial value) {
        return value.id();
    }
}
