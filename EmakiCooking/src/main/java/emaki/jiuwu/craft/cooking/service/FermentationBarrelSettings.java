package emaki.jiuwu.craft.cooking.service;

import java.util.List;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

final class FermentationBarrelSettings {

    private final Supplier<YamlSection> configuration;
    private final Supplier<YamlSection> guiConfiguration;

    FermentationBarrelSettings(Supplier<YamlSection> configuration, Supplier<YamlSection> guiConfiguration) {
        this.configuration = configuration;
        this.guiConfiguration = guiConfiguration;
    }

    boolean dropResult() {
        return configuration.get().getBoolean("stations.fermentation_barrel.drop_result", true);
    }

    boolean pauseWhenOpen() {
        return configuration.get().getBoolean("stations.fermentation_barrel.pause_when_open", true);
    }

    String inventoryTitle() {
        return guiConfiguration.get().getString("title", "<dark_gray>发酵桶");
    }

    int inventoryRows() {
        return Math.max(1, Math.min(6, guiConfiguration.get().getInt("rows", 3)));
    }

    List<Integer> ingredientSlots() {
        return CookingSettingsService.ingredientSlots(guiConfiguration.get(), inventoryRows() * 9, 7);
    }
}
