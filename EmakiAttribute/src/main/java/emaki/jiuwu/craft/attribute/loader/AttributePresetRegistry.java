package emaki.jiuwu.craft.attribute.loader;

import java.io.File;
import java.util.Map;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributePreset;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

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
    protected AttributePreset parse(File file, YamlSection configuration) {
        return AttributePreset.fromMap(configuration);
    }

    @Override
    protected boolean validateSchema(File file, YamlSection configuration) {
        boolean valid = true;
        if (Texts.isBlank(configuration.getString("id"))) {
            issue(
                    "loader.schema_missing_id",
                    Map.of(
                            "type", typeName(),
                            "file", file.getName(),
                            "field", "id"
                    )
            );
            valid = false;
        }
        Object values = configuration.get("values");
        if (values != null && !(values instanceof Map<?, ?> || values instanceof YamlSection)) {
            issue(
                    "loader.schema_invalid_section",
                    Map.of(
                            "type", typeName(),
                            "file", file.getName(),
                            "field", "values"
                    )
            );
            valid = false;
        }
        return valid;
    }

    @Override
    protected String idOf(AttributePreset value) {
        return value.id();
    }
}
