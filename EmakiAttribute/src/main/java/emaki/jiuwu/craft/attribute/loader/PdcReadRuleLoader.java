package emaki.jiuwu.craft.attribute.loader;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.PdcReadRule;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;

public final class PdcReadRuleLoader extends DirectoryLoader<PdcReadRule> {

    public PdcReadRuleLoader(EmakiAttributePlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "conditions";
    }

    @Override
    protected String typeName() {
        return "PDC 读取条件";
    }

    @Override
    protected void seedBundledResources(File directory) {
        List<String> bundledResources = YamlFiles.listResourcePaths(plugin, directoryName());
        String resourcePrefix = directoryName() + "/";
        for (String resourceName : bundledResources) {
            String relativePath = resourceName.startsWith(resourcePrefix)
                    ? resourceName.substring(resourcePrefix.length())
                    : resourceName;
            copyBundledResource(resourceName, new File(directory, relativePath));
        }
    }

    @Override
    protected boolean validateSchema(File file, YamlConfiguration configuration) {
        if (configuration == null || !configuration.getBoolean("enabled", true)) {
            return true;
        }
        if (Texts.isBlank(configuration.getString("source_id"))) {
            issue(
                    "loader.schema_missing_id",
                    Map.of(
                            "type", typeName(),
                            "file", file.getName(),
                            "field", "source_id"
                    )
            );
            return false;
        }
        return true;
    }

    @Override
    protected PdcReadRule parse(File file, YamlConfiguration configuration) {
        if (configuration == null || !configuration.getBoolean("enabled", true)) {
            return null;
        }
        return PdcReadRule.fromMap(configuration.getValues(false));
    }

    @Override
    protected String idOf(PdcReadRule value) {
        return value == null ? null : value.sourceId();
    }
}
