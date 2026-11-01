package emaki.jiuwu.craft.cooking.loader;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationType;

public final class RecipeParseProbe {

    private RecipeParseProbe() {
    }

    public static RecipeDocument parse(JavaPlugin plugin, StationType stationType, File file,
            YamlSection configuration) {
        if (stationType == null || file == null || configuration == null) {
            return null;
        }
        return switch (stationType) {
            case CHOPPING_BOARD -> new ChoppingBoardRecipeLoader(plugin).parse(file, configuration);
            case WOK -> new WokRecipeLoader(plugin).parse(file, configuration);
            case GRINDER -> new GrinderRecipeLoader(plugin).parse(file, configuration);
            case STEAMER -> new SteamerRecipeLoader(plugin).parse(file, configuration);
            case OVEN -> new OvenRecipeLoader(plugin).parse(file, configuration);
            case JUICER -> new JuicerRecipeLoader(plugin).parse(file, configuration);
            case FERMENTATION_BARREL -> new FermentationBarrelRecipeLoader(plugin).parse(file, configuration);
        };
    }
}
