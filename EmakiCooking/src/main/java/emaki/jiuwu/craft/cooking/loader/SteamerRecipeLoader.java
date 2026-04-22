package emaki.jiuwu.craft.cooking.loader;

import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.cooking.model.StationType;

public final class SteamerRecipeLoader extends BaseRecipeLoader {

    public SteamerRecipeLoader(JavaPlugin plugin) {
        super(plugin, StationType.STEAMER, List.of(
                "schema_version",
                "id",
                "display_name",
                "input.source",
                "required_steam"
        ));
    }
}
