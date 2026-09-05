package emaki.jiuwu.craft.cooking.legacy;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyTargetSpec;

public final class CookingLegacyTargets {

    private static final String CONFIG = "config.yml";

    private static final List<LegacyTargetSpec> SPECS = List.of(
            LegacyTargetSpec.replaceAnd(CONFIG, "stations.chopping_board",
                    "tool_item_sources", "tool_matcher"),
            LegacyTargetSpec.replaceAnd(CONFIG, "stations.wok",
                    "spatula_item_sources", "spatula_matcher"),
            LegacyTargetSpec.replaceAnd(CONFIG, "stations.juicer",
                    "container_item_sources", "container_matcher"),
            LegacyTargetSpec.replaceAnd(CONFIG, "stations.steamer.fuels[]",
                    "item_sources", "matcher"),
            LegacyTargetSpec.replaceAnd(CONFIG, "stations.oven.fuels[]",
                    "item_sources", "matcher"),
            LegacyTargetSpec.replaceAnd(CONFIG, "stations.steamer.moisture_rules[]",
                    "input_item_sources", "input_matcher"),
            LegacyTargetSpec.replaceAnd(CONFIG, "nutrition.food_sources[]",
                    "item_sources", "matcher"),
            LegacyTargetSpec.replaceAnd("recipes/juicer", "container",
                    "item_sources", "matcher"),
            LegacyTargetSpec.replaceAnd("recipes", "input",
                    "item_sources", "matcher"),
            LegacyTargetSpec.replaceAnd("recipes/wok", "ingredients[]",
                    "item_sources", "matcher"),
            LegacyTargetSpec.replaceAnd("recipes/fermentation_barrel", "inputs[]",
                    "item_sources", "matcher").retainingLegacyKey(),
            LegacyTargetSpec.replace("recipes", "result.*.outputs[]",
                    "item_sources", "item_source"));

    private CookingLegacyTargets() {
    }

    public static @NotNull List<LegacyTargetSpec> specs() {
        return SPECS;
    }
}
