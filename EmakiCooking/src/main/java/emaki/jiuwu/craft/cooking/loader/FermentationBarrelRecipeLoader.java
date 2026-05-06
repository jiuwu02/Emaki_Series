package emaki.jiuwu.craft.cooking.loader;

import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.cooking.model.StationType;

public final class FermentationBarrelRecipeLoader extends BaseRecipeLoader {

    public FermentationBarrelRecipeLoader(JavaPlugin plugin) {
        super(plugin, StationType.FERMENTATION_BARREL, List.of(
                "id",
                "display_name",
                "inputs",
                "fermentation_time_seconds"
        ));
    }
}
