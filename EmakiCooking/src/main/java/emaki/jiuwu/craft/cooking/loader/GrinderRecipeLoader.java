package emaki.jiuwu.craft.cooking.loader;

import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.cooking.model.StationType;

public final class GrinderRecipeLoader extends BaseRecipeLoader {

    public GrinderRecipeLoader(JavaPlugin plugin) {
        super(plugin, StationType.GRINDER, List.of(
                "id",
                "display_name",
                "input.source",
                "grind_time_seconds"
        ));
    }
}
