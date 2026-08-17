package emaki.jiuwu.craft.strengthen.enhancement.recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.quantity.Quantity;
import emaki.jiuwu.craft.strengthen.enhancement.cost.CurrencyConfig;
import emaki.jiuwu.craft.strengthen.enhancement.cost.MaterialSlotConfig;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityCounterConfig;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityDecayConfig;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityDecayTypeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityEffectConfig;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityEffectTypeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityScopeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityTriggerConfig;

public final class EnhancementRecipeParser {

    private EnhancementRecipeParser() {
    }

    public static @Nullable EnhancementRecipe parse(@Nullable YamlSection section) {
        if (section == null) {
            return null;
        }

        String id = section.getString("id");
        if (Texts.isBlank(id)) {
            return null;
        }

        String mode = section.getString("mode", "");
        if (Texts.isBlank(mode)) {
            return null;
        }

        EnhancementRecipe.TargetConfig target = parseTarget(section.getSection("target"));
        if (target == null) {
            return null;
        }

        List<MaterialSlotConfig> materials = parseMaterials(section.getMapList("materials"));
        List<CurrencyConfig> costs = parseCosts(section.getMapList("costs"));
        Quantity chance = parseChance(MaterialSlotConfig.quantityNode(section, "chance"));
        EnhancementRecipe.PityConfig pity = parsePity(section.getSection("pity"));
        Map<String, List<String>> actions = parseActions(section.getSection("actions"));

        return new EnhancementRecipe(id, mode, target, materials, costs, chance, pity, actions);
    }

    private static @Nullable EnhancementRecipe.TargetConfig parseTarget(@Nullable YamlSection section) {
        if (section == null) {
            return null;
        }

        String provider = section.getString("provider", "");
        if (Texts.isBlank(provider)) {
            return null;
        }

        YamlSection filterSection = section.getSection("filter");
        Map<String, Object> filter = null;
        if (filterSection != null) {
            Map<String, Object> filterMap = new LinkedHashMap<>();
            for (String key : filterSection.getKeys(false)) {
                Object value = filterSection.get(key);
                if (value != null) {
                    filterMap.put(key, value);
                }
            }
            if (!filterMap.isEmpty()) {
                filter = filterMap;
            }
        }

        return new EnhancementRecipe.TargetConfig(provider, filter);
    }

    private static @NotNull List<MaterialSlotConfig> parseMaterials(@Nullable List<Map<?, ?>> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return List.of();
        }

        List<MaterialSlotConfig> materials = new ArrayList<>();
        for (Map<?, ?> raw : rawList) {
            if (raw == null) {
                continue;
            }
            MaterialSlotConfig config = MaterialSlotConfig.fromConfig(new MapYamlSection(castKeys(raw)));
            if (config != null) {
                materials.add(config);
            }
        }
        return List.copyOf(materials);
    }

    private static @NotNull List<CurrencyConfig> parseCosts(@Nullable List<Map<?, ?>> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return List.of();
        }

        List<CurrencyConfig> costs = new ArrayList<>();
        for (Map<?, ?> raw : rawList) {
            if (raw == null) {
                continue;
            }
            CurrencyConfig config = CurrencyConfig.fromConfig(new MapYamlSection(castKeys(raw)));
            if (config != null) {
                costs.add(config);
            }
        }
        return List.copyOf(costs);
    }

    private static @NotNull Map<String, Object> castKeys(@NotNull Map<?, ?> raw) {
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return converted;
    }

    private static @NotNull Quantity parseChance(@Nullable Object config) {
        Quantity quantity = Quantity.fromConfig(config);
        if (quantity == null) {
            return Quantity.fixed(1.0);
        }
        return quantity;
    }

    private static @Nullable EnhancementRecipe.PityConfig parsePity(@Nullable YamlSection section) {
        if (section == null) {
            return null;
        }

        PityScopeEnum scope = parseEnum(section.getString("scope", "item"), PityScopeEnum.class, PityScopeEnum.ITEM);
        String group = section.getString("group", "");
        if (Texts.isBlank(group)) {
            return null;
        }
        PityCounterConfig counter = new PityCounterConfig(scope, group);

        PityTriggerConfig trigger;
        if (section.contains("threshold")) {
            int threshold = section.getInt("threshold", 10);
            trigger = PityTriggerConfig.threshold(threshold);
        } else if (section.contains("formula")) {
            Quantity formula = Quantity.fromConfig(MaterialSlotConfig.quantityNode(section, "formula"));
            if (formula == null) {
                return null;
            }
            trigger = PityTriggerConfig.formula(formula);
        } else {
            trigger = PityTriggerConfig.threshold(10);
        }

        PityEffectTypeEnum effectType = parseEnum(
                section.getString("effect", "force_success"),
                PityEffectTypeEnum.class,
                PityEffectTypeEnum.FORCE_SUCCESS
        );
        Double bonusValue = section.contains("bonus_value") ? section.getDouble("bonus_value") : null;
        PityEffectConfig effect = new PityEffectConfig(effectType, bonusValue);

        PityDecayConfig decay = parsePityDecay(section.getSection("decay"));

        return new EnhancementRecipe.PityConfig(counter, trigger, effect, decay);
    }

    private static @Nullable PityDecayConfig parsePityDecay(@Nullable YamlSection section) {
        if (section == null) {
            return null;
        }

        PityDecayTypeEnum type = parseEnum(
                section.getString("type", "reset"),
                PityDecayTypeEnum.class,
                PityDecayTypeEnum.RESET
        );
        double value = section.getDouble("value", 0.0);

        return new PityDecayConfig(type, value);
    }

    private static <E extends Enum<E>> E parseEnum(@Nullable String value, Class<E> enumClass, E defaultValue) {
        if (Texts.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumClass, value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    private static @NotNull Map<String, List<String>> parseActions(@Nullable YamlSection section) {
        if (section == null) {
            return Map.of();
        }

        Map<String, List<String>> actions = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            List<String> actionLines = section.getStringList(key);
            if (!actionLines.isEmpty()) {
                actions.put(key, List.copyOf(actionLines));
            }
        }
        return Map.copyOf(actions);
    }
}
