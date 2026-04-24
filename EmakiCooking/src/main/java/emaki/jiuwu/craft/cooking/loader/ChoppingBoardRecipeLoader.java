package emaki.jiuwu.craft.cooking.loader;

import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.cooking.model.StationType;

public final class ChoppingBoardRecipeLoader extends BaseRecipeLoader {

    public ChoppingBoardRecipeLoader(JavaPlugin plugin) {
        super(plugin, StationType.CHOPPING_BOARD, List.of(
                "schema_version",
                "id",
                "display_name",
                "input.source",
                "cuts_required"
        ));
    }
}
