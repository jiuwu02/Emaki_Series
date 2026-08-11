package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

final class OvenSettings {

    private final Supplier<YamlSection> configuration;
    private final Supplier<YamlSection> guiConfiguration;

    OvenSettings(Supplier<YamlSection> configuration, Supplier<YamlSection> guiConfiguration) {
        this.configuration = configuration;
        this.guiConfiguration = guiConfiguration;
    }

    boolean dropResult() {
        return configuration.get().getBoolean("stations.oven.drop_result", true);
    }

    String inventoryTitle() {
        return guiConfiguration.get().getString("title", "<dark_gray>烤炉");
    }

    int inventoryRows() {
        int rows = guiConfiguration.get().getInt("rows", 1);
        return Math.max(1, Math.min(6, rows));
    }

    List<Integer> ingredientSlots() {
        return CookingSettingsService.ingredientSlots(guiConfiguration.get(), inventoryRows() * 9, 5);
    }

    List<CookingSettingsService.OvenFuelRule> fuels() {
        List<CookingSettingsService.OvenFuelRule> result = new ArrayList<>();
        for (Map<?, ?> entry : configuration.get().getMapList("stations.oven.fuels")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            ItemSourceRef source = ItemSourceUtil.parse(normalized.get("item_sources"));
            if (source == null) {
                continue;
            }
            Integer duration = CookingSettingsService.configurationValueToInt(normalized.get("duration_seconds"), 0);
            Integer heat = CookingSettingsService.configurationValueToInt(normalized.get("heat"), 0);
            result.add(new CookingSettingsService.OvenFuelRule(
                    source,
                    duration == null ? 0 : Math.max(0, duration),
                    heat == null ? 0 : Math.max(0, heat)
            ));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    int heatMin() {
        return Math.max(0, configuration.get().getInt("stations.oven.heat.min", 20));
    }

    int heatMax() {
        return Math.max(heatMin(), configuration.get().getInt("stations.oven.heat.max", 80));
    }

    int heatDecayPerSecond() {
        return Math.max(0, configuration.get().getInt("stations.oven.heat.decay_per_second", 5));
    }
}
