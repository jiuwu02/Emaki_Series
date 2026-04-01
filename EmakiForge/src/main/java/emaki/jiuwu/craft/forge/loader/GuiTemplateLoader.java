package emaki.jiuwu.craft.forge.loader;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;

import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateParser;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;

public final class GuiTemplateLoader extends YamlDirectoryLoader<GuiTemplate> {

    public GuiTemplateLoader(EmakiForgePlugin plugin) {
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
    protected GuiTemplate parse(File file, YamlConfiguration configuration) {
        return GuiTemplateParser.parse(configuration);
    }

    @Override
    protected String idOf(GuiTemplate value) {
        return value.id();
    }
}
