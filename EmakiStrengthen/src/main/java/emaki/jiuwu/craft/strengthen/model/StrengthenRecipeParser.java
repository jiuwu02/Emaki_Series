package emaki.jiuwu.craft.strengthen.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;
import emaki.jiuwu.craft.corelib.condition.ConditionNode;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
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

public final class StrengthenRecipeParser {

    private StrengthenRecipeParser() {
    }

    public static StrengthenRecipe parse(YamlSection section) {
        if (section == null) {
            return null;
        }
        String id = section.getString("id");
        if (Texts.isBlank(id)) {
            return null;
        }
        ConditionBlock condition = ConditionBlock.fromRoot(section, true, false);
        ConditionGroup conditionGroup = condition.group();
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
                toApiConditionGroup(conditionGroup),
                conditionGroup.conditionType(),
                conditionGroup.requiredCount(),
                parseBranchTree(section.getSection("branch_tree")),
                section.get("name_actions"),
                section.get("lore_actions")
        );
    }

    private static StrengthenConditionGroup toApiConditionGroup(ConditionGroup group) {
        if (group == null) {
            return StrengthenConditionGroup.empty();
        }
        return new StrengthenConditionGroup(
                group.conditionType(),
                group.requiredCount(),
                group.conditions().stream()
                        .map(StrengthenRecipeParser::toApiConditionNode)
                        .filter(java.util.Objects::nonNull)
                        .toList()
        );
    }

    private static StrengthenConditionNode toApiConditionNode(ConditionNode node) {
        if (node == null) {
            return null;
        }
        if (node.groupNode()) {
            return StrengthenConditionNode.group(toApiConditionGroup(node.group()));
        }
        return new StrengthenConditionNode(node.type(), node.expression(), null, node.data());
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
                    parseStageVariables(stageSection),
                    parseStageAttributes(stageSection),
                    parseSkillEffects(stageSection.getMapList("effects")),
                    parseStageMaterials(stageSection.getMapList("materials")),
                    parseEconomyOverride(stageSection.getSection("economy_override")),
                    parseActionLines(stageSection.getSection("actions"), "success"),
                    parseActionLines(stageSection.getSection("actions"), "failure"),
                    parseStageDisplayActions(stageSection, "name_action", "name_actions"),
                    parseStageDisplayActions(stageSection, "lore_action", "lore_actions")
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
                    parseMaterialItem(rawEntry),
                    Numbers.tryParseInt(ConfigNodes.get(rawEntry, "amount"), 1),
                    ConfigNodes.bool(rawEntry, "optional", false),
                    ConfigNodes.bool(rawEntry, "protection", false),
                    Numbers.tryParseInt(ConfigNodes.get(rawEntry, "temper_boost"), 0)
            ));
        }
        return List.copyOf(result);
    }

    static List<String> parseActionLines(YamlSection section, String key) {
        return section == null ? List.of() : section.getStringList(key);
    }

    static Object parseStageDisplayActions(YamlSection section, String type, String key) {
        if (section == null) {
            return null;
        }
        List<Object> actions = new ArrayList<>();
        appendStageDisplayActions(actions, section.get(key));
        for (Map<?, ?> rawEffect : section.getMapList("effects")) {
            if (!type.equals(Texts.lower(ConfigNodes.string(rawEffect, "type", "")))) {
                continue;
            }
            appendStageDisplayActions(actions, ConfigNodes.get(rawEffect, key));
            appendStageDisplayActions(actions, ConfigNodes.get(rawEffect, type));
        }
        return actions.isEmpty() ? null : List.copyOf(actions);
    }

    private static void appendStageDisplayActions(List<Object> actions, Object raw) {
        if (actions == null || raw == null) {
            return;
        }
        Object plain = ConfigNodes.toPlainData(raw);
        if (plain instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (entry != null) {
                    actions.add(ConfigNodes.toPlainData(entry));
                }
            }
            return;
        }
        actions.add(plain);
    }

    static String parseMaterialItem(Object rawEntry) {
        ItemSource source = ItemSourceUtil.parse(ConfigNodes.get(rawEntry, "item_sources"));
        String shorthand = ItemSourceUtil.toShorthand(source);
        return shorthand == null ? "" : shorthand;
    }

    static List<String> parseSkillEffects(List<Map<?, ?>> rawEffects) {
        if (rawEffects == null || rawEffects.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Map<?, ?> rawEffect : rawEffects) {
            if (!"es_skill".equals(Texts.lower(ConfigNodes.string(rawEffect, "type", "")))) {
                continue;
            }
            for (Object rawSkill : ConfigNodes.asObjectList(ConfigNodes.get(rawEffect, "es_skills"))) {
                String skillId = Texts.normalizeId(Texts.toStringSafe(rawSkill));
                if (Texts.isNotBlank(skillId)) {
                    result.add(skillId);
                }
            }
        }
        return List.copyOf(result);
    }

    static Map<String, Object> parseStageVariables(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>(parseVariablesMap(section.getSection("variables")));
        mergeEffectMap(values, section.getMapList("effects"), "variables", "variables");
        return values.isEmpty() ? Map.of() : Map.copyOf(values);
    }

    static Map<String, Object> parseStageAttributes(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>(parseVariablesMap(section.getSection("ea_attributes")));
        mergeEffectMap(values, section.getMapList("effects"), "ea_attribute", "ea_attributes");
        return values.isEmpty() ? Map.of() : Map.copyOf(values);
    }

    private static void mergeEffectMap(Map<String, Object> target, List<Map<?, ?>> rawEffects, String type, String key) {
        if (target == null || rawEffects == null || rawEffects.isEmpty()) {
            return;
        }
        for (Map<?, ?> rawEffect : rawEffects) {
            if (!type.equals(Texts.lower(ConfigNodes.string(rawEffect, "type", "")))) {
                continue;
            }
            for (Map.Entry<String, Object> entry : ConfigNodes.entries(ConfigNodes.get(rawEffect, key)).entrySet()) {
                if (Texts.isNotBlank(entry.getKey()) && entry.getValue() != null) {
                    target.put(Texts.lower(entry.getKey()), ConfigNodes.toPlainData(entry.getValue()));
                }
            }
        }
    }

    static Map<String, Object> parseVariablesMap(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object raw = section.get(key);
            if (raw == null) {
                continue;
            }
            String normalizedKey = Texts.lower(key);
            if (raw instanceof Number) {
                values.put(normalizedKey, raw);
            } else {
                String text = Texts.toStringSafe(raw).trim();
                if (Texts.isNotBlank(text)) {
                    Double numericValue = Numbers.tryParseDouble(text, null);
                    values.put(normalizedKey, numericValue != null ? numericValue : text);
                }
            }
        }
        return values;
    }

    static StrengthenBranchNode parseBranchTree(YamlSection section) {
        if (section == null) {
            return null;
        }
        return parseBranchNode(section, "root");
    }

    private static StrengthenBranchNode parseBranchNode(YamlSection section, String defaultId) {
        if (section == null) {
            return null;
        }
        String branchId = section.getString("branch_id", defaultId);
        String displayName = section.getString("display_name", "");
        int forkAfterStar = Numbers.tryParseInt(section.get("fork_after_star"), -1);
        Map<Integer, StarStage> stages = parseStars(section.getSection("stars"));
        Map<String, StrengthenBranchNode> children = new LinkedHashMap<>();
        YamlSection childrenSection = section.getSection("children");
        if (childrenSection != null) {
            for (String childKey : childrenSection.getKeys(false)) {
                YamlSection childSection = childrenSection.getSection(childKey);
                if (childSection == null) {
                    continue;
                }
                StrengthenBranchNode childNode = parseBranchNode(childSection, childKey);
                if (childNode != null) {
                    children.put(childKey, childNode);
                }
            }
        }
        if (stages.isEmpty() && children.isEmpty()) {
            return null;
        }
        return new StrengthenBranchNode(branchId, displayName, stages, forkAfterStar, children);
    }
}
