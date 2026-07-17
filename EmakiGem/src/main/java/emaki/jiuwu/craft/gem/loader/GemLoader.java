package emaki.jiuwu.craft.gem.loader;

import java.io.File;
import java.util.Map;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;

public final class GemLoader extends YamlDirectoryLoader<GemDefinition> {

    public GemLoader(EmakiGemPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "gems";
    }

    @Override
    protected String typeName() {
        return localized("loader.type.gem");
    }

    @Override
    protected GemDefinition parse(File file, YamlSection configuration) {
        if (configuration == null) {
            issue("loader.invalid_config", Map.of("type", typeName(), "file", file == null ? "-" : file.getName()));
            return null;
        }
        String id = Texts.lower(configuration.getString("id"));
        if (Texts.isBlank(id)) {
            onBlankId(file);
            return null;
        }
        ItemSource itemSource = ItemSourceUtil.parse(configuration.get("item_sources"));
        if (itemSource == null) {
            issue("loader.gem_missing_item_source", Map.of(
                    "file", file.getName(),
                    "id", id,
                    "item_sources", String.valueOf(configuration.get("item_sources"))
            ));
            return null;
        }
        GemDefinition definition = GemDefinition.fromConfig(configuration);
        if (definition == null) {
            issue("loader.invalid_config", Map.of("type", typeName(), "file", file.getName()));
        }
        return definition;
    }

    @Override
    protected String idOf(GemDefinition value) {
        return value.id();
    }
}
