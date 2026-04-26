package emaki.jiuwu.craft.gem.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.gem.model.GemDefinition.CostConfig;
import emaki.jiuwu.craft.gem.model.GemDefinition.CurrencyCost;
import emaki.jiuwu.craft.gem.model.GemDefinition.ExtractReturn;
import emaki.jiuwu.craft.gem.model.GemDefinition.GemUpgradeLevel;
import emaki.jiuwu.craft.gem.model.GemDefinition.MaterialCost;
import emaki.jiuwu.craft.gem.model.GemDefinition.UpgradeConfig;

final class GemDefinitionParser {

    private GemDefinitionParser() {
    }

    static GemDefinition parse(String fallbackId, YamlSection section) {
        if (section == null) {
            return null;
        }
        String id = Texts.lower(section.getString("id", fallbackId));
        if (Texts.isBlank(id)) {
            return null;
        }
        ItemSource itemSource = ItemSourceUtil.parse(section.get("item_source"));
        if (itemSource == null) {
            return null;
        }
        Set<String> socketCompatibility = new LinkedHashSet<>();
        for (String value : section.getStringList("socket_compatibility")) {
            if (Texts.isNotBlank(value)) {
                socketCompatibility.add(Texts.lower(value));
            }
        }
        return new GemDefinition(
                id,
                section.getString("display_name", id),
                section.getStringList("lore"),
                section.getString("gem_type", "universal"),
                section.getInt("tier", 1),
                itemSource,
                Numbers.tryParseInt(section.get("custom_model_data"), null),
                parseStatMap(section.getSection("stats")),
                parseStatMap(section.getSection("attributes")),
                parseSkillEffects(section.getMapList("effects")),
                socketCompatibility,
                section.get("structured_presentation"),
                parseCostConfig(section.getSection("inlay_cost")),
                parseCostConfig(section.getSection("extract_cost")),
                parseExtractReturn(section.getSection("extract_return")),
                parseUpgradeConfig(section.getSection("upgrade")),
                section.getStringList("inlay_success_actions"),
                section.getStringList("extract_success_actions")
        );
    }

    static CostConfig parseCostConfig(YamlSection section) {
        if (section == null) {
            return CostConfig.none();
        }
        List<CurrencyCost> currencies = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("currencies")) {
            CurrencyCost currencyCost = parseCurrencyCost(map);
            if (currencyCost != null) {
                currencies.add(currencyCost);
            }
        }
        List<MaterialCost> materials = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("materials")) {
            MaterialCost materialCost = parseMaterialCost(map);
            if (materialCost != null) {
                materials.add(materialCost);
            }
        }
        return new CostConfig(currencies, materials);
    }

    static CurrencyCost parseCurrencyCost(Object raw) {
        Double configuredAmount = Numbers.tryParseDouble(ConfigNodes.get(raw, "amount"), null);
        double baseCost = Numbers.tryParseDouble(ConfigNodes.get(raw, "base_cost"), 0D);
        String costFormula = ConfigNodes.string(raw, "cost_formula", "");
        if ((configuredAmount == null || configuredAmount <= 0D) && baseCost <= 0D && Texts.isBlank(costFormula)) {
            return null;
        }
        return new CurrencyCost(
                ConfigNodes.string(raw, "provider", "auto"),
                ConfigNodes.string(raw, "currency_id", ""),
                configuredAmount == null ? -1D : configuredAmount,
                baseCost,
                costFormula,
                ConfigNodes.string(raw, "display_name", "")
        );
    }

    static MaterialCost parseMaterialCost(Object raw) {
        ItemSource source = ItemSourceUtil.parse(raw);
        if (source == null) {
            return null;
        }
        return new MaterialCost(source, Numbers.tryParseInt(ConfigNodes.get(raw, "amount"), 1));
    }

    static ExtractReturn parseExtractReturn(YamlSection section) {
        if (section == null) {
            return ExtractReturn.defaults();
        }
        String mode = section.getString("mode", "original");
        return new ExtractReturn(
                mode,
                section.getInt("downgrade_levels", 1),
                section.getDouble("degraded_chance", 0D)
        );
    }

    static UpgradeConfig parseUpgradeConfig(YamlSection section) {
        if (section == null) {
            return UpgradeConfig.disabled();
        }
        YamlSection economySection = section.getSection("economy");
        List<CurrencyCost> currencies = new ArrayList<>();
        List<Map<?, ?>> configuredCurrencies = economySection != null ? economySection.getMapList("currencies") : section.getMapList("currencies");
        for (Map<?, ?> map : configuredCurrencies) {
            CurrencyCost currencyCost = parseCurrencyCost(map);
            if (currencyCost != null) {
                currencies.add(currencyCost);
            }
        }
        Map<Integer, Double> successRates = new LinkedHashMap<>();
        YamlSection successRatesSection = section.getSection("success_rates");
        if (successRatesSection != null) {
            for (String key : successRatesSection.getKeys(false)) {
                Integer targetLevel = Numbers.tryParseInt(key, null);
                Double rate = Numbers.tryParseDouble(successRatesSection.get(key), null);
                if (targetLevel != null && targetLevel > 1 && rate != null) {
                    successRates.put(targetLevel, rate);
                }
            }
        }
        Map<Integer, GemUpgradeLevel> levels = new LinkedHashMap<>();
        YamlSection levelsSection = section.getSection("levels");
        if (levelsSection != null) {
            for (String key : levelsSection.getKeys(false)) {
                Integer targetLevel = Numbers.tryParseInt(key, null);
                if (targetLevel == null || targetLevel <= 1) {
                    continue;
                }
                GemUpgradeLevel level = parseGemUpgradeLevel(targetLevel, levelsSection.getSection(key));
                if (level != null) {
                    levels.put(level.targetLevel(), level);
                }
            }
        }
        return new UpgradeConfig(
                section.getBoolean("enabled", false),
                section.getInt("max_level", levels.isEmpty() ? 1 : levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(1)),
                currencies,
                successRates,
                section.getString("gui_template", ""),
                section.getString("failure_penalty", "none"),
                levels
        );
    }

    static GemUpgradeLevel parseGemUpgradeLevel(int targetLevel, YamlSection section) {
        if (section == null) {
            return null;
        }
        List<MaterialCost> materials = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("materials")) {
            MaterialCost materialCost = parseMaterialCost(map);
            if (materialCost != null) {
                materials.add(materialCost);
            }
        }
        YamlSection economySection = section.getSection("economy");
        List<CurrencyCost> currencies = new ArrayList<>();
        List<Map<?, ?>> configuredCurrencies = economySection != null ? economySection.getMapList("currencies") : section.getMapList("currencies");
        for (Map<?, ?> map : configuredCurrencies) {
            CurrencyCost currencyCost = parseCurrencyCost(map);
            if (currencyCost != null) {
                currencies.add(currencyCost);
            }
        }
        double successChance = section.contains("success_rate")
                ? section.getDouble("success_rate", 100D)
                : section.contains("success_chance")
                        ? section.getDouble("success_chance", 100D)
                        : -1D;
        return new GemUpgradeLevel(
                targetLevel,
                section.getString("display_name", ""),
                parseStatMap(section.getSection("stats")),
                parseStatMap(section.getSection("attributes")),
                parseSkillEffects(section.getMapList("effects")),
                section.get("structured_presentation"),
                successChance,
                currencies,
                section.getString("failure_penalty", ""),
                materials,
                section.getStringList("success_actions"),
                section.getStringList("failure_actions")
        );
    }

    static Map<String, Double> parseStatMap(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Double> stats = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Double value = Numbers.tryParseDouble(section.get(key), null);
            if (value != null) {
                stats.put(Texts.lower(key), value);
            }
        }
        return Map.copyOf(stats);
    }

    static List<String> parseSkillEffects(List<Map<?, ?>> rawEffects) {
        if (rawEffects == null || rawEffects.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Map<?, ?> rawEffect : rawEffects) {
            if (!"skill".equals(Texts.lower(ConfigNodes.string(rawEffect, "type", "")))) {
                continue;
            }
            for (Object rawSkill : ConfigNodes.asObjectList(ConfigNodes.get(rawEffect, "skills"))) {
                String skillId = Texts.normalizeId(Texts.toStringSafe(rawSkill));
                if (Texts.isNotBlank(skillId)) {
                    result.add(skillId);
                }
            }
            String skillId = Texts.normalizeId(ConfigNodes.string(rawEffect, "skill", ""));
            if (Texts.isNotBlank(skillId)) {
                result.add(skillId);
            }
        }
        return normalizeSkillIds(result);
    }

    private static List<String> normalizeSkillIds(List<String> rawSkillIds) {
        if (rawSkillIds == null || rawSkillIds.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String rawSkillId : rawSkillIds) {
            String skillId = Texts.normalizeId(rawSkillId);
            if (Texts.isNotBlank(skillId) && seen.add(skillId)) {
                result.add(skillId);
            }
        }
        return List.copyOf(result);
    }
}
