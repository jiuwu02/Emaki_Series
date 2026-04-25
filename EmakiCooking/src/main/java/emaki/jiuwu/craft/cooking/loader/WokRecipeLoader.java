package emaki.jiuwu.craft.cooking.loader;

import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.cooking.model.StationType;

public final class WokRecipeLoader extends BaseRecipeLoader {

    public WokRecipeLoader(JavaPlugin plugin) {
        super(plugin, StationType.WOK, List.of(
                "id",
                "display_name",
                "ingredients",
                "heat_level",
                "stir_total.min",
                "stir_total.max",
                "fault_tolerance"
        ));
    }
}
