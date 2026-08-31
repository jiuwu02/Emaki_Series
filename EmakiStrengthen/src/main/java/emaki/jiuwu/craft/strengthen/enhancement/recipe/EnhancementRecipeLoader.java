package emaki.jiuwu.craft.strengthen.enhancement.recipe;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;

public final class EnhancementRecipeLoader extends YamlDirectoryLoader<EnhancementRecipe> {

    public EnhancementRecipeLoader(@NotNull JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "enhancement_recipes";
    }

    @Override
    protected String typeName() {
        return "Enhancement Recipe";
    }

    @Override
    protected EnhancementRecipe parse(File file, YamlSection configuration) {
        return EnhancementRecipeParser.parse(configuration);
    }

    @Override
    protected String idOf(EnhancementRecipe value) {
        return value == null ? null : value.id();
    }
}
