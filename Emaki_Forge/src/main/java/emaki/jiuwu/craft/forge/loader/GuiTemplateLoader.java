package emaki.jiuwu.craft.forge.loader;

import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateParser;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;

public final class GuiTemplateLoader extends AbstractDirectoryLoader<GuiTemplate> {

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
