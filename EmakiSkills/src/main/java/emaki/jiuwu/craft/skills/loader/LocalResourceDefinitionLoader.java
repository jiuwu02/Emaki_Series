package emaki.jiuwu.craft.skills.loader;

import java.io.File;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.skills.model.LocalResourceDefinition;

public final class LocalResourceDefinitionLoader extends YamlDirectoryLoader<LocalResourceDefinition> {

    public LocalResourceDefinitionLoader(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "resources";
    }

    @Override
    protected String typeName() {
        return "resource";
    }

    @Override
    protected LocalResourceDefinition parse(File file, YamlSection configuration) {
        String fallbackId = baseName(file);
        if (configuration == null) {
            issue("loader.invalid_config", Map.of("type", typeName(), "file", file == null ? "-" : file.getName()));
            return null;
        }
        String id = Texts.lower(configuration.getString("id", fallbackId));
        if (Texts.isBlank(id)) {
            onBlankId(file);
            return null;
        }

        return new LocalResourceDefinition(
                id,
                configuration.getString("display_name", id),
                configuration.getDouble("max", 100D),
                configuration.getDouble("default_current", 100D),
                configuration.getDouble("regen_amount", 0D),
                configuration.getInt("regen_interval_ticks", 0),
                configuration.getDouble("clamp_min", 0D),
                configuration.getDouble("clamp_max", 0D)
        );
    }

    @Override
    protected String idOf(LocalResourceDefinition value) {
        return value.id();
    }

    private String baseName(File file) {
        String name = file == null ? "" : file.getName();
        int dot = name.lastIndexOf('.');
        return Texts.lower(dot >= 0 ? name.substring(0, dot) : name);
    }
}
