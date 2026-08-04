package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

final class WokSettings {

    private final Supplier<YamlSection> configuration;

    WokSettings(Supplier<YamlSection> configuration) {
        this.configuration = configuration;
    }

    double displayLayoutRadius() {
        return Math.max(0D, configuration.get().getDouble("display_entities.wok.layout_radius", 0.26D));
    }

    boolean dropResult() {
        return configuration.get().getBoolean("stations.wok.drop_result", true);
    }

    boolean needBowl() {
        return configuration.get().getBoolean("stations.wok.need_bowl", true);
    }

    long stirDelayMs() {
        return Math.max(0L, configuration.get().getInt("stations.wok.stir_delay_ms", 5000));
    }

    long timeoutMs() {
        return Math.max(0L, configuration.get().getInt("stations.wok.timeout_ms", 30000));
    }

    List<ItemSourceRef> spatulaSources() {
        return CookingSettingsService.parseSources(configuration.get().get("stations.wok.spatula_item_sources"));
    }

    List<CookingSettingsService.HeatLevelRule> heatLevels() {
        List<CookingSettingsService.HeatLevelRule> result = new ArrayList<>();
        for (Map<?, ?> entry : configuration.get().getMapList("stations.wok.heat_levels")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            ItemSourceRef source = ItemSourceUtil.parse(normalized.get("item_sources"));
            if (source == null) {
                continue;
            }
            ItemSourceRef litSource = CookingSettingsService.parseLitSource(normalized);
            ItemSourceRef unlitSource = CookingSettingsService.parseUnlitSource(normalized);
            Integer level = CookingSettingsService.configurationValueToInt(normalized.get("level"), 0);
            result.add(new CookingSettingsService.HeatLevelRule(source, litSource, unlitSource == null ? source : unlitSource, level == null ? 0 : Math.max(0, level)));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    boolean igniteHeatSource() {
        return configuration.get().getBoolean("stations.wok.ignite_heat_source", true);
    }

    boolean scaldDamageEnabled() {
        return configuration.get().getBoolean("stations.wok.scald_damage.enabled", true);
    }

    int scaldDamageValue() {
        return Math.max(0, configuration.get().getInt("stations.wok.scald_damage.value", 2));
    }

    boolean stirAnimationEnabled() {
        return configuration.get().getBoolean("stations.wok.stir_animation.enabled", true);
    }

    int stirAnimationDurationTicks() {
        return Math.max(2, configuration.get().getInt("stations.wok.stir_animation.duration_ticks", 10));
    }

    double stirAnimationHeight() {
        return Math.max(0.0D, configuration.get().getDouble("stations.wok.stir_animation.height", 0.4D));
    }

    String stirAnimationAxis() {
        String axis = Texts.lower(configuration.get().getString("stations.wok.stir_animation.rotation_axis", "x"));
        return switch (axis) {
            case "x", "y", "z" -> axis;
            default -> "x";
        };
    }

    double stirAnimationRotation() {
        return configuration.get().getDouble("stations.wok.stir_animation.rotation_degrees", 360.0D);
    }

    boolean failureEnabled() {
        return configuration.get().getBoolean("stations.wok.failure.enabled", true);
    }

    int failureChance() {
        return Math.max(0, configuration.get().getInt("stations.wok.failure.chance", 5));
    }

    String failureOutputSource() {
        return firstSourceShorthand(configuration.get().get("stations.wok.failure.item_sources"));
    }

    String invalidResultSource() {
        return firstSourceShorthand(configuration.get().get("stations.wok.invalid_result_item_sources"));
    }

    private String firstSourceShorthand(Object raw) {
        ItemSourceRef source = ItemSourceUtil.parse(raw);
        String shorthand = ItemSourceUtil.toShorthand(source);
        return shorthand == null ? "" : shorthand;
    }
}
