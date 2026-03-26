package emaki.jiuwu.craft.forge.loader;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.Blueprint;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

public final class BlueprintLoader extends AbstractDirectoryLoader<Blueprint> {

    public BlueprintLoader(EmakiForgePlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "blueprints";
    }

    @Override
    protected String typeName() {
        return "blueprint";
    }

    @Override
    protected Blueprint parse(File file, YamlConfiguration configuration) {
        return Blueprint.fromConfig(configuration);
    }

    @Override
    protected String idOf(Blueprint value) {
        return value.id();
    }

    public List<Blueprint> getByTag(String tag) {
        List<Blueprint> result = new ArrayList<>();
        if (Texts.isBlank(tag)) {
            return result;
        }
        for (Blueprint blueprint : items.values()) {
            if (blueprint.hasTag(tag)) {
                result.add(blueprint);
            }
        }
        return result;
    }
}
