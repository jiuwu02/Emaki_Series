package emaki.jiuwu.craft.cooking.service;

import java.util.List;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;

final class JuicerSettings {

    private final Supplier<YamlSection> configuration;
    private final Supplier<YamlSection> guiConfiguration;

    JuicerSettings(Supplier<YamlSection> configuration, Supplier<YamlSection> guiConfiguration) {
        this.configuration = configuration;
        this.guiConfiguration = guiConfiguration;
    }

    boolean dropResult() {
        return configuration.get().getBoolean("stations.juicer.drop_result", true);
    }

    boolean requireContainer() {
        return configuration.get().getBoolean("stations.juicer.require_container", true);
    }

    ItemRequirement containerRequirement() {
        return CookingMatchers.requirementWithLegacyFallback(
                configuration.get().getSection("stations.juicer"),
                "container",
                "container_item_sources",
                "container_matcher");
    }

    int maxFluidMl() {
        return Math.max(1, configuration.get().getInt("stations.juicer.max_fluid_ml", 1000));
    }

    int defaultServingMl() {
        return Math.max(1, configuration.get().getInt("stations.juicer.default_serving_ml", 250));
    }

    String inventoryTitle() {
        return guiConfiguration.get().getString("title", "<dark_gray>榨汁机");
    }

    int inventoryRows() {
        return Math.max(1, Math.min(6, guiConfiguration.get().getInt("rows", 1)));
    }

    List<Integer> ingredientSlots() {
        return CookingSettingsService.ingredientSlots(guiConfiguration.get(), inventoryRows() * 9, 5);
    }
}
