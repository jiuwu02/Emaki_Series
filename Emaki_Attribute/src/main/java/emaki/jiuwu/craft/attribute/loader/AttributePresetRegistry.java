package emaki.jiuwu.craft.attribute.loader;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributePreset;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AttributePresetRegistry extends DirectoryLoader<AttributePreset> {

    public AttributePresetRegistry(EmakiAttributePlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "presets";
    }

    @Override
    protected String typeName() {
        return plugin.messageService() == null ? "预设" : plugin.messageService().message("label.preset");
    }

    @Override
    protected AttributePreset parse(File file, YamlConfiguration configuration) {
        return AttributePreset.fromMap(configuration);
    }

    @Override
    protected String idOf(AttributePreset value) {
        return value.id();
    }
}
