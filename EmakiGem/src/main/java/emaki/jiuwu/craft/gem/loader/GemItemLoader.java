package emaki.jiuwu.craft.gem.loader;

import java.io.File;

import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;

public final class GemItemLoader extends YamlDirectoryLoader<GemItemDefinition> {

    public GemItemLoader(EmakiGemPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "items";
    }

    @Override
    protected String typeName() {
        return localized("loader.type.gem_item");
    }

    @Override
    protected GemItemDefinition parse(File file, YamlSection configuration) {
        return GemItemDefinition.fromConfig(configuration);
    }

    @Override
    protected String idOf(GemItemDefinition value) {
        return value.id();
    }
}
