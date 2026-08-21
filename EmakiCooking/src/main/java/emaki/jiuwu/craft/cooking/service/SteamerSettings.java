package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

final class SteamerSettings {

    private final Supplier<YamlSection> configuration;
    private final Supplier<YamlSection> guiConfiguration;

    SteamerSettings(Supplier<YamlSection> configuration, Supplier<YamlSection> guiConfiguration) {
        this.configuration = configuration;
        this.guiConfiguration = guiConfiguration;
    }

    boolean dropResult() {
        return configuration.get().getBoolean("stations.steamer.drop_result", true);
    }

    String inventoryTitle() {
        return guiConfiguration.get().getString("title", "<dark_gray>蒸锅");
    }

    int inventoryRows() {
        int rows = guiConfiguration.get().getInt("rows", 1);
        return Math.max(1, Math.min(6, rows));
    }

    List<Integer> ingredientSlots() {
        return CookingSettingsService.ingredientSlots(guiConfiguration.get(), inventoryRows() * 9, 5);
    }

    List<ItemSourceRef> heatSources() {
        return CookingSettingsService.parseSources(configuration.get().get("stations.steamer.heat_item_sources"));
    }

    List<CookingSettingsService.HeatSourceIgnitionRule> heatSourceIgnitionRules() {
        return parseHeatSourceIgnitionRules(configuration.get().get("stations.steamer.heat_item_sources"));
    }

    boolean igniteHeatSource() {
        return configuration.get().getBoolean("stations.steamer.ignite_heat_source", true);
    }

    List<CookingSettingsService.SteamerFuelRule> fuels() {
        List<CookingSettingsService.SteamerFuelRule> result = new ArrayList<>();
        for (Map<?, ?> entry : configuration.get().getMapList("stations.steamer.fuels")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            ItemSourceRef source = ItemSourceUtil.parse(normalized.get("item_sources"));
            if (source == null) {
                continue;
            }
            Integer duration = CookingSettingsService.configurationValueToInt(normalized.get("duration_seconds"), 0);
            result.add(new CookingSettingsService.SteamerFuelRule(
                    source,
                    duration == null ? 0 : Math.max(0, duration),
                    CookingMatchers.parse(normalized, "matcher")));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    List<CookingSettingsService.SteamerMoistureRule> moistureSources() {
        List<CookingSettingsService.SteamerMoistureRule> result = new ArrayList<>();
        for (Map<?, ?> entry : configuration.get().getMapList("stations.steamer.moisture_rules")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            ItemSourceRef input = ItemSourceUtil.parse(normalized.get("input_item_sources"));
            if (input == null) {
                continue;
            }
            ItemSourceRef output = ItemSourceUtil.parse(normalized.get("item_sources"));
            Integer moisture = CookingSettingsService.configurationValueToInt(normalized.get("moisture"), 0);
            result.add(new CookingSettingsService.SteamerMoistureRule(
                    input,
                    output,
                    moisture == null ? 0 : Math.max(0, moisture),
                    CookingMatchers.parse(normalized, "input_matcher")));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    boolean resetProgressWhenSteamEmpty() {
        return configuration.get().getBoolean("stations.steamer.reset_progress_when_steam_empty", true);
    }

    int steamProductionEfficiency() {
        return Math.max(0, configuration.get().getInt("stations.steamer.steam_production_efficiency", 10));
    }

    int steamConversionEfficiency() {
        return Math.max(0, configuration.get().getInt("stations.steamer.steam_conversion_efficiency", 1));
    }

    int steamConsumptionEfficiency() {
        return Math.max(0, configuration.get().getInt("stations.steamer.steam_consumption_efficiency", 1));
    }

    private List<CookingSettingsService.HeatSourceIgnitionRule> parseHeatSourceIgnitionRules(Object raw) {
        List<CookingSettingsService.HeatSourceIgnitionRule> result = new ArrayList<>();
        for (Object token : ConfigNodes.asObjectList(raw)) {
            if (token instanceof Map<?, ?> map) {
                Map<String, Object> normalized = MapYamlSection.normalizeMap(map);
                ItemSourceRef source = ItemSourceUtil.parse(CookingSettingsService.firstPresent(
                        normalized,
                        "item_sources",
                        "source",
                        "item"
                ));
                if (source == null) {
                    continue;
                }
                ItemSourceRef litSource = CookingSettingsService.parseLitSource(normalized);
                ItemSourceRef unlitSource = CookingSettingsService.parseUnlitSource(normalized);
                result.add(new CookingSettingsService.HeatSourceIgnitionRule(source, litSource, unlitSource == null ? source : unlitSource));
                continue;
            }
            ItemSourceRef source = ItemSourceUtil.parse(token);
            if (source != null) {
                result.add(new CookingSettingsService.HeatSourceIgnitionRule(source, null, source));
            }
        }
        return List.copyOf(result);
    }
}
