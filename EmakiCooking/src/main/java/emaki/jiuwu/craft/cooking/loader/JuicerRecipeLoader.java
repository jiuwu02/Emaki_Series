package emaki.jiuwu.craft.cooking.loader;

import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.cooking.model.StationType;

public final class JuicerRecipeLoader extends BaseRecipeLoader {

    public JuicerRecipeLoader(JavaPlugin plugin) {
        super(plugin, StationType.JUICER, List.of(
                "id",
                "display_name",
                "input.item_sources|input.matcher",
                "presses_required"
        ));
    }
}
