package emaki.jiuwu.craft.gem.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.gem.model.GemDefinition.CostConfig;
import emaki.jiuwu.craft.gem.model.GemDefinition.CurrencyCost;
import emaki.jiuwu.craft.gem.model.GemDefinition.ExtractReturn;
import emaki.jiuwu.craft.gem.model.GemDefinition.GemStage;
import emaki.jiuwu.craft.gem.model.GemDefinition.MaterialCost;
import emaki.jiuwu.craft.gem.model.GemDefinition.StageConfig;

final class GemDefinitionParser {

    private GemDefinitionParser() {
    }

    static GemDefinition parse(YamlSection section) {
        if (section == null) {
            return null;
        }
        String id = Texts.lower(section.getString("id"));
        if (Texts.isBlank(id)) {
            return null;
        }
        ItemSourceRef itemSource = ItemSourceUtil.parse(section.get("item_sources"));
        if (itemSource == null) {
            return null;
        }
        YamlSection matcherSection = section.getSection("matcher");
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
                section.getInt("level", 1),
                itemSource,
                matcherSection == null ? null : Matcher.fromConfig(matcherSection),
                Numbers.tryParseInt(section.get("custom_model_data"), null),
                parseVariables(section),
                parseAttributes(section),
                parseSkillEffects(section.getMapList("effects")),
                socketCompatibility,
                section.getStringList("required_gems"),
                section.getStringList("conflicting_gems"),
                parseNameActions(section),
                parseLoreActions(section),
                parseCostConfig(section.getSection("inlay_cost")),
                parseCostConfig(section.getSection("extract_cost")),
                parseExtractReturn(section.getSection("extract_return")),
                parseStageConfig(section.contains("stages")
                        ? section.getSection("stages")
                        : section.getSection("upgrade")),
                parseRerollConfig(section.getSection("reroll")),
                parseActionLines(section.getSection("actions"), "inlay_success"),
                parseActionLines(section.getSection("actions"), "extract_success")
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
        ItemSourceRef source = ItemSourceUtil.parse(raw);
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

    static GemDefinition.RerollConfig parseRerollConfig(YamlSection section) {
        if (section == null) {
            return GemDefinition.RerollConfig.disabled();
        }
        List<String> diagnostics = new ArrayList<>();
        boolean enabled = section.getBoolean("enabled", true);
        String group = section.getString("group", "default");
        int maxAffixes = Math.max(1, section.getInt("max_affixes", 1));
        Map<String, List<GemDefinition.AffixPoolEntry>> pools = new LinkedHashMap<>();
        YamlSection poolSection = section.getSection("pools");
        if (poolSection == null) {
            poolSection = section.getSection("affixes");
        }
        if (poolSection != null) {
            for (String key : poolSection.getKeys(false)) {
                Object raw = poolSection.get(key);
                List<GemDefinition.AffixPoolEntry> entries = new ArrayList<>();
                if (raw instanceof Iterable<?> iterable) {
                    for (Object value : iterable) {
                        GemDefinition.AffixPoolEntry entry = parseAffixPoolEntry(value, diagnostics);
                        if (entry != null) {
                            entries.add(entry);
                        }
                    }
                } else if (raw instanceof YamlSection nested) {
                    for (String entryKey : nested.getKeys(false)) {
                        GemDefinition.AffixPoolEntry entry = parseAffixPoolEntry(nested.get(entryKey), diagnostics, entryKey);
                        if (entry != null) {
                            entries.add(entry);
                        }
                    }
                }
                if (!entries.isEmpty()) {
                    pools.put(Texts.lower(key), List.copyOf(entries));
                } else {
                    diagnostics.add("reroll pool '" + key + "' is empty or invalid");
                }
            }
        }
        return new GemDefinition.RerollConfig(
                enabled,
                group,
                maxAffixes,
                pools,
                CostConfig.fromConfig(section.getSection("full_cost")),
                CostConfig.fromConfig(section.getSection("value_cost")),
                diagnostics
        );
    }

    private static GemDefinition.AffixPoolEntry parseAffixPoolEntry(Object raw, List<String> diagnostics) {
        return parseAffixPoolEntry(raw, diagnostics, null);
    }

    private static GemDefinition.AffixPoolEntry parseAffixPoolEntry(Object raw,
            List<String> diagnostics,
            String fallbackId) {
        String id = Texts.isBlank(fallbackId) ? ConfigNodes.string(raw, "id", "") : fallbackId;
        if (Texts.isBlank(id)) {
            diagnostics.add("reroll affix entry missing id");
            return null;
        }
        double weight = Numbers.tryParseDouble(ConfigNodes.get(raw, "weight"), 1D);
        double minValue = Numbers.tryParseDouble(ConfigNodes.get(raw, "min"),
                Numbers.tryParseDouble(ConfigNodes.get(raw, "min_value"), 0D));
        double maxValue = Numbers.tryParseDouble(ConfigNodes.get(raw, "max"),
                Numbers.tryParseDouble(ConfigNodes.get(raw, "max_value"), minValue));
        if (weight <= 0D) {
            diagnostics.add("reroll affix '" + id + "' has non-positive weight");
        }
        return new GemDefinition.AffixPoolEntry(
                id,
                weight,
                Numbers.tryParseInt(ConfigNodes.get(raw, "min_stage"), 1),
                Numbers.tryParseInt(ConfigNodes.get(raw, "max_stage"), Integer.MAX_VALUE),
                minValue,
                maxValue,
                ConfigNodes.string(raw, "display_name", id),
                ConfigNodes.string(raw, "attribute_id", id)
        );
    }

    static StageConfig parseStageConfig(YamlSection section) {
        if (section == null) {
            return StageConfig.disabled();
        }
        Map<Integer, GemStage> stages = new LinkedHashMap<>();
        YamlSection configuredStages = section.getSection("levels");
        if (configuredStages == null) {
            configuredStages = section;
        }
        for (String key : configuredStages.getKeys(false)) {
            Integer targetLevel = Numbers.tryParseInt(key, null);
            if (targetLevel == null || targetLevel <= 1) {
                continue;
            }
            GemStage stage = parseGemStage(targetLevel, configuredStages.getSection(key));
            if (stage != null) {
                stages.put(stage.targetLevel(), stage);
            }
        }
        int configuredMaxLevel = stages.isEmpty()
                ? 1
                : stages.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
        return new StageConfig(
                section.getBoolean("enabled", !stages.isEmpty()),
                section.getInt("max_level", configuredMaxLevel),
                section.getString("gui_template", ""),
                stages
        );
    }

    static GemStage parseGemStage(int targetLevel, YamlSection section) {
        if (section == null) {
            return null;
        }
        return new GemStage(
                targetLevel,
                section.getString("display_name", ""),
                parseVariables(section),
                parseAttributes(section),
                parseSkillEffects(section.getMapList("effects")),
                parseNameActions(section),
                parseLoreActions(section),
                parseStringMap(section.getSection("matrices"))
        );
    }

    static Map<String, String> parseStringMap(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if (Texts.isNotBlank(key) && section.get(key) != null) {
                values.put(Texts.lower(key), Texts.toStringSafe(section.get(key)));
            }
        }
        return Map.copyOf(values);
    }

    static Object parseNameActions(YamlSection section) {
        if (section == null) {
            return null;
        }
        Object topLevel = section.get("name_actions");
        return topLevel != null ? topLevel : firstEffectPayload(section.getMapList("effects"), "name_action", "name_actions");
    }

    static Object parseLoreActions(YamlSection section) {
        if (section == null) {
            return null;
        }
        Object topLevel = section.get("lore_actions");
        return topLevel != null ? topLevel : firstEffectPayload(section.getMapList("effects"), "lore_action", "lore_actions");
    }

    static Map<String, Object> parseVariables(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(parseStatMap(section.getSection("variables")));
        result.putAll(parseEffectStatMap(section.getMapList("effects"), "variables", "variables"));
        return Map.copyOf(result);
    }

    static Map<String, Object> parseAttributes(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(parseStatMap(section.getSection("ea_attributes")));
        result.putAll(parseEffectStatMap(section.getMapList("effects"), "ea_attribute", "ea_attributes"));
        return Map.copyOf(result);
    }

    static List<String> parseActionLines(YamlSection section, String key) {
        return section == null ? List.of() : section.getStringList(key);
    }

    static Map<String, Object> parseStatMap(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = ConfigNodes.toPlainData(section.get(key));
            if (value != null) {
                stats.put(Texts.lower(key), value);
            }
        }
        return Map.copyOf(stats);
    }

    static Map<String, Object> parseEffectStatMap(List<Map<?, ?>> rawEffects, String type, String key) {
        if (rawEffects == null || rawEffects.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<?, ?> rawEffect : rawEffects) {
            if (!type.equals(Texts.lower(ConfigNodes.string(rawEffect, "type", "")))) {
                continue;
            }
            for (Map.Entry<String, Object> entry : ConfigNodes.entries(ConfigNodes.get(rawEffect, key)).entrySet()) {
                if (entry.getValue() != null) {
                    result.put(Texts.lower(entry.getKey()), ConfigNodes.toPlainData(entry.getValue()));
                }
            }
        }
        return Map.copyOf(result);
    }

    static Object firstEffectPayload(List<Map<?, ?>> rawEffects, String type, String key) {
        if (rawEffects == null || rawEffects.isEmpty()) {
            return null;
        }
        for (Map<?, ?> rawEffect : rawEffects) {
            if (type.equals(Texts.lower(ConfigNodes.string(rawEffect, "type", "")))) {
                return ConfigNodes.get(rawEffect, key);
            }
        }
        return null;
    }

    static List<String> parseSkillEffects(List<Map<?, ?>> rawEffects) {
        if (rawEffects == null || rawEffects.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
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
