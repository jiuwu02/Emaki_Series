package emaki.jiuwu.craft.skills.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.skills.model.CostOperation;
import emaki.jiuwu.craft.skills.model.ResourceCostType;
import emaki.jiuwu.craft.skills.model.SkillActivationType;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillParameterDefinition;
import emaki.jiuwu.craft.skills.model.SkillParameterType;
import emaki.jiuwu.craft.skills.model.SkillResourceCost;
import emaki.jiuwu.craft.skills.model.SkillUpgradeConfig;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.script.SkillScriptDefinition;
import emaki.jiuwu.craft.skills.script.SkillScriptMode;
import emaki.jiuwu.craft.skills.script.SkillScriptPhase;

public final class SkillDefinitionLoader extends YamlDirectoryLoader<SkillDefinition> {

    private final EmakiSkillsPlugin skillsPlugin;

    public SkillDefinitionLoader(EmakiSkillsPlugin plugin) {
        super(plugin);
        this.skillsPlugin = plugin;
    }

    @Override
    protected String directoryName() {
        return "skills";
    }

    @Override
    protected String typeName() {
        return localized("loader.type.skill");
    }

    @Override
    protected SkillDefinition parse(File file, YamlSection configuration) {
        if (configuration == null) {
            issue("loader.invalid_config", Map.of("type", typeName(), "file", file == null ? "-" : file.getName()));
            return null;
        }
        String id = Texts.lower(configuration.getString("id"));
        if (Texts.isBlank(id)) {
            onBlankId(file);
            return null;
        }
        boolean enabled = configuration.getBoolean("enabled", true);
        if (!enabled) {
            return null;
        }

        List<SkillResourceCost> resourceCosts = parseResourceCosts(configuration.getMapList("resource_costs"));
        String iconMaterial = Texts.lower(configuration.getString("icon_material", ""));
        SkillActivationType activationType = SkillActivationType.fromString(
                configuration.getString("trigger_type", "active"));
        ConditionBlock condition = ConditionBlock.fromRoot(configuration, true, false);
        ConditionGroup conditionGroup = condition.group();

        String cronExpr = configuration.getString("cron_expression", "");
        int cronMaxExec = configuration.getInt("cron_max_executions", 0);
        List<String> rawPassiveTriggers = normalizeTriggerIds(configuration.getStringList("passive_triggers"));
        List<String> passiveTriggers;
        if (!cronExpr.isBlank()) {
            List<String> withCron = new ArrayList<>(rawPassiveTriggers);
            String cronTriggerId = "cron_" + id;
            if (!withCron.contains(cronTriggerId)) {
                withCron.add(cronTriggerId);
            }
            passiveTriggers = List.copyOf(withCron);
        } else {
            passiveTriggers = rawPassiveTriggers;
        }

        return new SkillDefinition(
                id,
                configuration.getString("display_name", id),
                configuration.getStringList("description"),
                iconMaterial,
                configuration.getString("mythic_skill", ""),
                activationType,
                passiveTriggers,
                cronExpr,
                cronMaxExec,
                parseSkillParameters(configuration.getSection("variables")),
                parseScript(configuration.getSection("script")),
                parseUpgradeConfig(configuration.getSection("upgrade")),
                configuration.getInt("cooldown_ticks", 0),
                configuration.getInt("global_cooldown_ticks", 0),
                resourceCosts,
                configuration.getStringList("lore_aliases"),
                configuration.getString("pdc_skill_id", id),
                parseIdList(configuration.get("tags"), null),
                parseIdList(configuration.get("tab_tags"), configuration.get("tabTags")),
                parseIdList(configuration.get("required_skills"), configuration.get("dependencies")),
                parseIdList(configuration.get("conflicting_skills"), configuration.get("conflicts")),
                configuration.getString("ui_category", "default"),
                configuration.getInt("sort_order", 0),
                configuration.getBoolean("show_in_slots", true),
                enabled,
                conditionGroup,
                conditionGroup.conditionType()
        );
    }

    @Override
    protected String idOf(SkillDefinition value) {
        return value.id();
    }

    private Map<String, SkillParameterDefinition> parseSkillParameters(YamlSection section) {
        if (section == null || section.getKeys(false).isEmpty()) {
            return Map.of();
        }
        Map<String, SkillParameterDefinition> parameters = new LinkedHashMap<>();
        for (String rawId : section.getKeys(false)) {
            String id = Texts.normalizeId(rawId);
            if (id.isBlank() || id.startsWith("emaki_")) {
                continue;
            }
            YamlSection parameterSection = section.getSection(rawId);
            SkillParameterDefinition definition;
            if (parameterSection == null) {
                definition = new SkillParameterDefinition(
                        id,
                        SkillParameterType.STRING,
                        Texts.toStringSafe(section.get(rawId)),
                        null,
                        null,
                        0,
                        ""
                );
            } else {
                SkillParameterType type = resolveParameterType(parameterSection);
                definition = new SkillParameterDefinition(
                        id,
                        type,
                        parameterConfig(parameterSection, type),
                        parameterSection.getDouble("min", null),
                        parameterSection.getDouble("max", null),
                        intValue(parameterSection.getInt("decimals", 0), 0),
                        parameterSection.getString("default", "")
                );
            }
            parameters.put(definition.id(), definition);
        }
        return Collections.unmodifiableMap(parameters);
    }

    private SkillParameterType resolveParameterType(YamlSection section) {
        String configuredType = section.getString("type", "");
        if (Texts.isNotBlank(configuredType)) {
            return SkillParameterType.fromString(configuredType);
        }

        if (hasRandomTextLines(section)) {
            return SkillParameterType.RANDOM_TEXT;
        }
        if (section.contains("min") && section.contains("max")) {
            return SkillParameterType.RANGE;
        }
        return SkillParameterType.CONSTANT;
    }

    private Object parameterConfig(YamlSection section, SkillParameterType type) {
        if (section == null) {
            return "";
        }
        if (type == SkillParameterType.STRING && hasRandomTextLines(section)) {
            Map<String, Object> config = new LinkedHashMap<>(ConfigNodes.entries(section));
            config.put("type", SkillParameterType.RANDOM_TEXT.configType());
            config.entrySet().removeIf(entry -> entry.getValue() == null);
            return Map.copyOf(config);
        }
        if (type == SkillParameterType.STRING || type == SkillParameterType.BOOLEAN) {
            return section.getString("value", "");
        }
        Map<String, Object> config = new LinkedHashMap<>(ConfigNodes.entries(section));
        config.put("type", type.configType());
        config.entrySet().removeIf(entry -> entry.getValue() == null);
        return Map.copyOf(config);
    }

    private boolean hasRandomTextLines(YamlSection section) {
        return section != null && section.contains("lines");
    }

    private SkillScriptDefinition parseScript(YamlSection section) {
        AppConfig.ScriptEngineSettings scriptEngine = skillsPlugin.appConfig().scriptEngine();
        if (!scriptEngine.enabled()) {
            return SkillScriptDefinition.disabled();
        }
        if (section == null || section.getKeys(false).isEmpty()) {
            return SkillScriptDefinition.disabled();
        }
        SkillScriptMode mode = SkillScriptMode.fromString(
                section.getString("mode", scriptEngine.defaultMode()), SkillScriptMode.NATIVE);
        boolean stopOnFailure = section.getBoolean("stop_on_failure", scriptEngine.stopOnFailure());
        Map<SkillScriptPhase, List<String>> linesByPhase = new LinkedHashMap<>();
        YamlSection actions = section.getSection("actions");
        if (actions != null) {
            putPhaseLines(linesByPhase, SkillScriptPhase.CAST, actions, "cast");
            putPhaseLines(linesByPhase, SkillScriptPhase.HIT, actions, "hit");
            putPhaseLines(linesByPhase, SkillScriptPhase.MISS, actions, "miss");
            putPhaseLines(linesByPhase, SkillScriptPhase.FAIL, actions, "fail");
        }

        Map<SkillScriptPhase, List<String>> conditionsByPhase = new LinkedHashMap<>();
        YamlSection conditions = section.getSection("conditions");
        if (conditions != null) {
            putPhaseLines(conditionsByPhase, SkillScriptPhase.CAST, conditions, "cast");
            putPhaseLines(conditionsByPhase, SkillScriptPhase.HIT, conditions, "hit");
            putPhaseLines(conditionsByPhase, SkillScriptPhase.MISS, conditions, "miss");
            putPhaseLines(conditionsByPhase, SkillScriptPhase.FAIL, conditions, "fail");
        }

        boolean enabled = section.contains("enabled")
                ? section.getBoolean("enabled", false)
                : !linesByPhase.isEmpty();
        return new SkillScriptDefinition(enabled, mode, stopOnFailure, conditionsByPhase, linesByPhase);
    }

    private void putPhaseLines(Map<SkillScriptPhase, List<String>> target,
            SkillScriptPhase phase,
            YamlSection section,
            String key) {
        List<String> lines = section.getStringList(key);
        if (!lines.isEmpty()) {
            target.put(phase, List.copyOf(lines));
        }
    }

    private SkillUpgradeConfig parseUpgradeConfig(YamlSection section) {
        if (section == null || section.getKeys(false).isEmpty()) {
            return SkillUpgradeConfig.disabled();
        }
        boolean enabled = Boolean.TRUE.equals(section.getBoolean("enabled", false));
        int maxLevel = intValue(section.getInt("max_level", 1), 1);
        SkillUpgradeConfig.EconomyConfig economy = parseEconomyConfig(section.getSection("economy"));
        Map<Integer, Double> successRates = parseSuccessRates(section.getSection("success_rates"));
        Map<Integer, SkillUpgradeConfig.SkillUpgradeLevel> levels = parseUpgradeLevels(section.getSection("levels"));
        return new SkillUpgradeConfig(
                enabled,
                maxLevel,
                section.getString("gui_template", SkillUpgradeConfig.DEFAULT_GUI_TEMPLATE),
                economy,
                new emaki.jiuwu.craft.corelib.progression.TableProgression<>(successRates, 100D),
                section.getString("failure_penalty", "none"),
                levels
        );
    }

    private SkillUpgradeConfig.EconomyConfig parseEconomyConfig(YamlSection section) {
        if (section == null) {
            return SkillUpgradeConfig.EconomyConfig.disabled();
        }
        boolean enabled = Boolean.TRUE.equals(section.getBoolean("enabled", false));
        return new SkillUpgradeConfig.EconomyConfig(enabled, parseCurrencies(section.getMapList("currencies")));
    }

    private SkillUpgradeConfig.EconomyOverride parseEconomyOverride(YamlSection section) {
        if (section == null) {
            return null;
        }
        boolean enabled = section.getBoolean("enabled", true);
        List<SkillUpgradeConfig.CurrencyEntry> currencies = enabled
                ? parseCurrencies(section.getMapList("currencies"))
                : List.of();
        return new SkillUpgradeConfig.EconomyOverride(enabled, currencies);
    }

    private List<SkillUpgradeConfig.CurrencyEntry> parseCurrencies(List<Map<?, ?>> mapList) {
        if (mapList == null || mapList.isEmpty()) {
            return List.of();
        }
        List<SkillUpgradeConfig.CurrencyEntry> currencies = new ArrayList<>();
        for (Map<?, ?> map : mapList) {
            if (map == null || map.isEmpty()) {
                continue;
            }
            currencies.add(new SkillUpgradeConfig.CurrencyEntry(
                    Texts.toStringSafe(map.get("provider")),
                    Texts.toStringSafe(map.get("currency_id")),
                    parseDouble(map.get("base_cost"), 0D),
                    Texts.toStringSafe(map.get("cost_formula")),
                    Texts.toStringSafe(map.get("display_name"))
            ));
        }
        return List.copyOf(currencies);
    }

    private Map<Integer, Double> parseSuccessRates(YamlSection section) {
        if (section == null || section.getKeys(false).isEmpty()) {
            return Map.of();
        }
        Map<Integer, Double> successRates = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Integer level = parseInt(key, null);
            if (level == null || level <= 0) {
                continue;
            }
            successRates.put(level, section.getDouble(key, 100D));
        }
        return Map.copyOf(successRates);
    }

    private Map<Integer, SkillUpgradeConfig.SkillUpgradeLevel> parseUpgradeLevels(YamlSection section) {
        if (section == null || section.getKeys(false).isEmpty()) {
            return Map.of();
        }
        Map<Integer, SkillUpgradeConfig.SkillUpgradeLevel> levels = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Integer targetLevel = parseInt(key, null);
            if (targetLevel == null || targetLevel <= 1) {
                continue;
            }
            YamlSection levelSection = section.getSection(key);
            if (levelSection == null) {
                continue;
            }
            Double successRate = levelSection.contains("success_rate")
                    ? levelSection.getDouble("success_rate", 100D)
                    : null;
            SkillUpgradeConfig.SkillUpgradeLevel level = new SkillUpgradeConfig.SkillUpgradeLevel(
                    targetLevel,
                    parseMaterials(levelSection.getMapList("materials")),
                    parseEconomyOverride(levelSection.getSection("economy")),
                    successRate,
                    parseActionLines(levelSection.getSection("actions"), "success"),
                    parseActionLines(levelSection.getSection("actions"), "failure")
            );
            levels.put(targetLevel, level);
        }
        return Map.copyOf(levels);
    }

    private List<SkillUpgradeConfig.MaterialCost> parseMaterials(List<Map<?, ?>> mapList) {
        if (mapList == null || mapList.isEmpty()) {
            return List.of();
        }
        List<SkillUpgradeConfig.MaterialCost> materials = new ArrayList<>();
        for (Map<?, ?> map : mapList) {
            if (map == null || map.isEmpty()) {
                continue;
            }
            String item = Texts.asStringList(map.get("item_sources")).stream().findFirst().orElse("");
            Matcher matcher = map.get("matcher") == null ? null : Matcher.fromConfig(map.get("matcher"));
            if (item.isBlank() && matcher == null) {
                continue;
            }
            materials.add(new SkillUpgradeConfig.MaterialCost(
                    item,
                    intValue(parseInt(map.get("amount"), 1), 1),
                    parseBoolean(map.get("optional"), false),
                    parseBoolean(map.get("protection"), false),
                    matcher,
                    ItemRequirement.fromConfig(map)
            ));
        }
        return List.copyOf(materials);
    }

    private List<String> parseActionLines(YamlSection section, String key) {
        return section == null ? List.of() : section.getStringList(key);
    }

    private List<SkillResourceCost> parseResourceCosts(List<Map<?, ?>> mapList) {
        if (mapList == null || mapList.isEmpty()) {
            return List.of();
        }
        List<SkillResourceCost> costs = new ArrayList<>();
        for (Map<?, ?> map : mapList) {
            SkillResourceCost cost = parseResourceCost(map);
            if (cost != null) {
                costs.add(cost);
            }
        }
        return costs;
    }

    private List<String> parseIdList(Object primary, Object secondary) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String rawId : Texts.asStringList(primary)) {
            String id = Texts.normalizeId(rawId).replace('-', '_');
            if (Texts.isNotBlank(id)) {
                ids.add(id);
            }
        }
        for (String rawId : Texts.asStringList(secondary)) {
            String id = Texts.normalizeId(rawId).replace('-', '_');
            if (Texts.isNotBlank(id)) {
                ids.add(id);
            }
        }
        return ids.isEmpty() ? List.of() : List.copyOf(ids);
    }

    private List<String> normalizeTriggerIds(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String rawId : rawIds) {
            String id = Texts.lower(rawId).replace('-', '_').trim();
            if (!id.isBlank() && !normalized.contains(id)) {
                normalized.add(id);
            }
        }
        return List.copyOf(normalized);
    }

    private SkillResourceCost parseResourceCost(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        ResourceCostType type = ResourceCostType.fromString(Texts.toStringSafe(map.get("type")));
        if (type == null) {
            return null;
        }
        String targetId = Texts.toStringSafe(map.get("target_id")).trim();
        double amount = parseDouble(map.get("amount"), 0D);
        CostOperation operation = CostOperation.fromString(Texts.toStringSafe(map.get("operation")));
        String failureMessage = Texts.toStringSafe(map.get("failure_message")).trim();
        return new SkillResourceCost(type, targetId, amount, operation, failureMessage);
    }

    private double parseDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(Texts.toStringSafe(value).trim());
        } catch (NumberFormatException _) {
            return defaultValue;
        }
    }

    private int intValue(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private Integer parseInt(Object value, Integer fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Texts.toStringSafe(value).trim());
        } catch (NumberFormatException _) {
            return fallback;
        }
    }

    private boolean parseBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = Texts.lower(value).trim();
        if ("true".equals(text) || "yes".equals(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "no".equals(text) || "0".equals(text)) {
            return false;
        }
        return fallback;
    }

}
