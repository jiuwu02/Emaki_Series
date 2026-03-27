package emaki.jiuwu.craft.attribute.loader;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.LoreFormatDefinition;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LoreFormatRegistry extends DirectoryLoader<LoreFormatDefinition> {

    public LoreFormatRegistry(EmakiAttributePlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "lore_formats";
    }

    @Override
    protected String typeName() {
        return plugin.messageService() == null ? "词条格式" : plugin.messageService().message("label.lore_format");
    }

    @Override
    protected LoreFormatDefinition parse(File file, YamlConfiguration configuration) {
        return LoreFormatDefinition.fromMap(configuration);
    }

    @Override
    protected String idOf(LoreFormatDefinition value) {
        return value.id();
    }
}
