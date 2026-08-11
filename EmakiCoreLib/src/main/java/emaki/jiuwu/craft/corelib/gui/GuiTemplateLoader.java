package emaki.jiuwu.craft.corelib.gui;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public class GuiTemplateLoader extends YamlDirectoryLoader<GuiTemplate> {

    public GuiTemplateLoader(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "gui";
    }

    @Override
    protected String typeName() {
        return "gui";
    }

    @Override
    protected GuiTemplate parse(File file, YamlSection configuration) {
        return GuiTemplateParser.parse(configuration, message -> {
            String detail = file.getName() + ": " + message;
            issues.add(detail);
            plugin.getLogger().warning("[gui] " + detail);
        });
    }

    @Override
    protected String idOf(GuiTemplate value) {
        return value.id();
    }
}
