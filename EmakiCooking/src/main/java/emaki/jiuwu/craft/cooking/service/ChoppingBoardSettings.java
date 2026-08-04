package emaki.jiuwu.craft.cooking.service;

import java.util.List;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

final class ChoppingBoardSettings {

    private final Supplier<YamlSection> configuration;

    ChoppingBoardSettings(Supplier<YamlSection> configuration) {
        this.configuration = configuration;
    }

    boolean dropResult() {
        return configuration.get().getBoolean("stations.chopping_board.drop_result", true);
    }

    boolean spaceRestriction() {
        return configuration.get().getBoolean("stations.chopping_board.space_restriction", false);
    }

    long interactionDelayMs() {
        return Math.max(0L, configuration.get().getInt("stations.chopping_board.interaction_delay_ms", 1000));
    }

    List<ItemSourceRef> toolSources() {
        return CookingSettingsService.parseSources(configuration.get().get("stations.chopping_board.tool_item_sources"));
    }

    boolean cutDamageEnabled() {
        return configuration.get().getBoolean("stations.chopping_board.cut_damage.enabled", true);
    }

    int cutDamageChance() {
        return Math.max(0, configuration.get().getInt("stations.chopping_board.cut_damage.chance", 10));
    }

    int cutDamageValue() {
        return Math.max(0, configuration.get().getInt("stations.chopping_board.cut_damage.value", 2));
    }
}
