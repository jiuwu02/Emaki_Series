package emaki.jiuwu.craft.attribute.loader;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.LoreFormatDefinition;
import java.io.File;
import java.util.List;
import java.util.Map;
import emaki.jiuwu.craft.corelib.text.Texts;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LoreFormatRegistry extends DirectoryLoader<LoreFormatDefinition> {

    private static final List<String> BUNDLED_RESOURCES = List.of(
        "default_flat.yml",
        "default_percent.yml",
        "default_regen.yml",
        "default_resource.yml"
    );

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
    protected void seedBundledResources(File directory) {
        for (String resourceName : BUNDLED_RESOURCES) {
            copyBundledResource("lore_formats/" + resourceName, new File(directory, resourceName));
        }
    }

    @Override
    protected boolean validateSchema(File file, YamlConfiguration configuration) {
        if (Texts.isBlank(configuration.getString("id"))) {
            issue(
                "loader.schema_missing_id",
                Map.of(
                    "type", typeName(),
                    "file", file.getName(),
                    "field", "id"
                )
            );
            return false;
        }
        return true;
    }

    @Override
    protected String idOf(LoreFormatDefinition value) {
        return value.id();
    }
}
