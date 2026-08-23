package emaki.jiuwu.craft.cooking.legacy;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyTargetSpec;

public final class CookingLegacyTargets {

    private static final String CONFIG = "config.yml";

    private static final List<LegacyTargetSpec> SPECS = List.of(
            LegacyTargetSpec.mergeAnd(CONFIG, "stations.chopping_board",
                    "tool_item_sources", "tool_matcher"),
            LegacyTargetSpec.mergeAnd(CONFIG, "stations.wok",
                    "spatula_item_sources", "spatula_matcher"),
            LegacyTargetSpec.mergeAnd(CONFIG, "stations.juicer",
                    "container_item_sources", "container_matcher"),
            LegacyTargetSpec.mergeAnd(CONFIG, "stations.steamer.fuels[]",
                    "item_sources", "matcher"),
            LegacyTargetSpec.mergeAnd(CONFIG, "stations.steamer.moisture_rules[]",
                    "input_item_sources", "input_matcher"),
            LegacyTargetSpec.mergeAnd(CONFIG, "nutrition.food_sources[]",
                    "item_sources", "matcher"),
            LegacyTargetSpec.mergeAnd("recipes/juicer", "container",
                    "item_sources", "matcher"),
            LegacyTargetSpec.mergeAnd("recipes", "input",
                    "item_sources", "matcher"),
            LegacyTargetSpec.mergeAnd("recipes/wok", "ingredients[]",
                    "item_sources", "matcher"));

    private CookingLegacyTargets() {
    }

    public static @NotNull List<LegacyTargetSpec> specs() {
        return SPECS;
    }
}
