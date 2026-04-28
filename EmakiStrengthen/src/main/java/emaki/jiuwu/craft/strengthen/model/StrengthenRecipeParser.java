package emaki.jiuwu.craft.strengthen.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe.CurrencyEntry;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe.EconomyConfig;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe.EconomyOverride;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe.Limits;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe.MatchRule;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe.StarStage;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe.StarStageMaterial;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe.StatLineDefinition;

final class StrengthenRecipeParser {

    private StrengthenRecipeParser() {
    }

    static StrengthenRecipe parse(YamlSection section) {
        if (section == null) {
            return null;
        }
        String id = section.getString("id");
        if (Texts.isBlank(id)) {
            return null;
        }
        return new StrengthenRecipe(
                id,
                section.getString("display_name", id),
                section.getString("gui_template", "strengthen_gui"),
                parseEconomy(section.getSection("economy")),
                parseLimits(section.getSection("limits")),
                parseSuccessRates(section.getSection("success_rates")),
                parseMatchRule(section.getSection("match")),
                parseStatLines(section.getSection("stat_lines")),
                parseStars(section.getSection("stars")),
                section.get("structured_presentation"),
                section.getStringList("conditions"),
                section.getString("condition_type", "all_of"),
                Numbers.tryParseInt(section.get("condition_required_count"), 0)
        );
    }

    static EconomyConfig parseEconomy(YamlSection section) {
        if (section == null) {
            return new EconomyConfig(false, List.of());
        }
        List<CurrencyEntry> currencies = parseCurrencies(section.getMapList("currencies"));
        Boolean enabledValue = section.getBoolean("enabled");
        boolean enabled = enabledValue != null ? enabledValue : !currencies.isEmpty();
        return new EconomyConfig(enabled, currencies);
    }

    static EconomyOverride parseEconomyOverride(YamlSection section) {
        if (section == null) {
            return new EconomyOverride(List.of());
        }
        return new EconomyOverride(parseCurrencies(section.getMapList("currencies")));
    }

    static List<CurrencyEntry> parseCurrencies(List<Map<?, ?>> rawEntries) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return List.of();
        }
        List<CurrencyEntry> currencies = new ArrayList<>();
        for (Map<?, ?> rawEntry : rawEntries) {
            if (rawEntry == null) {
                continue;
            }
            currencies.add(new CurrencyEntry(
                    ConfigNodes.string(rawEntry, "provider", ""),
                    ConfigNodes.string(rawEntry, "currency_id", ""),
                    Numbers.tryParseLong(ConfigNodes.get(rawEntry, "base_cost"), 0L),
                    ConfigNodes.string(rawEntry, "cost_formula", ""),
                    ConfigNodes.string(rawEntry, "display_name", "")
            ));
        }
        return List.copyOf(currencies);
    }

    static Limits parseLimits(YamlSection section) {
        if (section == null) {
            return Limits.defaults();
        }
        Limits defaults = Limits.defaults();
        return new Limits(
                Numbers.tryParseInt(section.get("max_star"), defaults.maxStar()),
                Numbers.tryParseInt(section.get("max_temper"), defaults.maxTemper()),
                Numbers.tryParseDouble(section.get("temper_chance_bonus_per_level"), defaults.temperChanceBonusPerLevel()),
                Numbers.tryParseDouble(section.get("success_chance_cap"), defaults.successChanceCap())
        );
    }

    static Map<Integer, Double> parseSuccessRates(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<Integer, Double> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Integer star = Numbers.tryParseInt(key, null);
            Double value = Numbers.tryParseDouble(section.get(key), null);
            if (star != null && value != null) {
                result.put(star, value);
            }
        }
        return result;
    }

    static MatchRule parseMatchRule(YamlSection section) {
        if (section == null) {
            return new MatchRule(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        return new MatchRule(
                section.getStringList("source_types"),
                section.getStringList("source_ids"),
                section.getStringList("source_patterns"),
                section.getStringList("slot_groups"),
                section.getStringList("lore_contains"),
                section.getStringList("stats_any")
        );
    }

    static Map<String, StatLineDefinition> parseStatLines(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, StatLineDefinition> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            YamlSection statSection = section.getSection(key);
            if (statSection == null) {
                String template = Texts.toStringSafe(section.get(key));
                result.put(Texts.lower(key), new StatLineDefinition(template, "", 0));
                continue;
            }
            result.put(Texts.lower(key), new StatLineDefinition(
                    statSection.getString("template", ""),
                    statSection.getString("section_id", ""),
                    Numbers.tryParseInt(statSection.get("section_order"), 0)
            ));
        }
        return result;
    }

    static Map<Integer, StarStage> parseStars(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<Integer, StarStage> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Integer targetStar = Numbers.tryParseInt(key, null);
            if (targetStar == null || targetStar <= 0) {
                continue;
            }
            YamlSection stageSection = section.getSection(key);
            if (stageSection == null) {
                continue;
            }
            result.put(targetStar, new StarStage(
                    targetStar,
                    stageSection.getString("name", ""),
                    parseDoubleMap(stageSection.getSection("stats")),
                    parseDoubleMap(stageSection.getSection("attributes")),
                    parseSkillEffects(stageSection.getMapList("effects")),
                    parseStageMaterials(stageSection.getMapList("materials")),
                    parseEconomyOverride(stageSection.getSection("economy_override")),
                    stageSection.get("structured_presentation"),
                    stageSection.getStringList("success_actions"),
                    stageSection.getStringList("failure_actions")
            ));
        }
        return result;
    }

    static List<StarStageMaterial> parseStageMaterials(List<Map<?, ?>> rawEntries) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return List.of();
        }
        List<StarStageMaterial> result = new ArrayList<>();
        for (Map<?, ?> rawEntry : rawEntries) {
            if (rawEntry == null) {
                continue;
            }
            result.add(new StarStageMaterial(
                    ConfigNodes.string(rawEntry, "item", ""),
                    Numbers.tryParseInt(ConfigNodes.get(rawEntry, "amount"), 1),
                    ConfigNodes.bool(rawEntry, "optional", false),
                    ConfigNodes.bool(rawEntry, "protection", false),
                    Numbers.tryParseInt(ConfigNodes.get(rawEntry, "temper_boost"), 0)
            ));
        }
        return List.copyOf(result);
    }

    static List<String> parseSkillEffects(List<Map<?, ?>> rawEffects) {
        if (rawEffects == null || rawEffects.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
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
        return List.copyOf(result);
    }

    static Map<String, Double> parseDoubleMap(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Double> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Double value = Numbers.tryParseDouble(section.get(key), null);
            if (value != null) {
                values.put(Texts.lower(key), value);
            }
        }
        return values;
    }
}
