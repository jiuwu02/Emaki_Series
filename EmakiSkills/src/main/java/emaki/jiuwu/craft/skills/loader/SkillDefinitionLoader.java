package emaki.jiuwu.craft.skills.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.skills.model.CostOperation;
import emaki.jiuwu.craft.skills.model.ResourceCostType;
import emaki.jiuwu.craft.skills.model.SkillActivationType;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillParameterDefinition;
import emaki.jiuwu.craft.skills.model.SkillParameterType;
import emaki.jiuwu.craft.skills.model.SkillResourceCost;
import emaki.jiuwu.craft.skills.model.SkillUpgradeConfig;

public final class SkillDefinitionLoader extends YamlDirectoryLoader<SkillDefinition> {

    public SkillDefinitionLoader(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "skills";
    }

    @Override
    protected String typeName() {
        return "skill";
    }

    @Override
    protected SkillDefinition parse(File file, YamlSection configuration) {
        String fallbackId = baseName(file);
        if (configuration == null) {
            issue("loader.invalid_config", Map.of("type", typeName(), "file", file == null ? "-" : file.getName()));
            return null;
        }
        String id = Texts.lower(configuration.getString("id", fallbackId));
        if (Texts.isBlank(id)) {
            onBlankId(file);
            return null;
        }
        boolean enabled = configuration.getBoolean("enabled", true);
        if (!enabled) {
            return null;
        }

        List<SkillResourceCost> resourceCosts = parseResourceCosts(configuration.getMapList("resource_costs"));
        String iconMaterial = configuration.getString("icon_material", "");
        SkillActivationType activationType = SkillActivationType.fromString(
                configuration.getString("trigger_type", "active"));

        return new SkillDefinition(
                id,
                configuration.getString("display_name", id),
                configuration.getStringList("description"),
                iconMaterial,
                configuration.getString("mythic_skill", ""),
                activationType,
                normalizeTriggerIds(configuration.getStringList("passive_triggers")),
                parseSkillParameters(configuration.getSection("skill_parameters")),
                parseUpgradeConfig(configuration.getSection("upgrade")),
                configuration.getInt("cooldown_ticks", 0),
                configuration.getInt("global_cooldown_ticks", 0),
                resourceCosts,
                configuration.getStringList("lore_aliases"),
                configuration.getString("pdc_skill_id", id),
                configuration.getString("ui_category", "default"),
                configuration.getInt("sort_order", 0),
                enabled,
                configuration.getStringList("conditions"),
                configuration.getString("condition_type", "all_of")
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
        return Map.copyOf(parameters);
    }

    private SkillParameterType resolveParameterType(YamlSection section) {
        String configuredType = section.getString("type", "");
        if (Texts.isNotBlank(configuredType)) {
            return SkillParameterType.fromString(configuredType);
        }
        if (Texts.isNotBlank(section.getString("formula", ""))
                || Texts.isNotBlank(section.getString("expression", ""))) {
            return SkillParameterType.EXPRESSION;
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
            return firstNotBlank(
                    section.getString("formula", ""),
                    firstNotBlank(section.getString("expression", ""), section.getString("value", ""))
            );
        }
        Map<String, Object> config = new LinkedHashMap<>(ConfigNodes.entries(section));
        config.put("type", type.configType());
        if (type == SkillParameterType.EXPRESSION) {
            config.put("expression", firstNotBlank(
                    section.getString("formula", ""),
                    firstNotBlank(section.getString("expression", ""), section.getString("value", ""))
            ));
        } else if (type == SkillParameterType.CONSTANT && !config.containsKey("value")) {
            String value = firstNotBlank(
                    section.getString("formula", ""),
                    firstNotBlank(section.getString("expression", ""), section.getString("default", ""))
            );
            if (Texts.isNotBlank(value)) {
                config.put("value", value);
            }
        }
        config.entrySet().removeIf(entry -> entry.getValue() == null);
        return Map.copyOf(config);
    }

    private boolean hasRandomTextLines(YamlSection section) {
        return section != null
                && (section.contains("lines")
                || section.contains("values")
                || section.contains("options")
                || section.contains("texts"));
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
                section.getString("gui_template", "upgrade/default"),
                economy,
                successRates,
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
                    parseSkillParameters(levelSection.getSection("parameters")),
                    levelSection.getStringList("success_actions"),
                    levelSection.getStringList("failure_actions")
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
            String item = Texts.toStringSafe(map.get("item"));
            if (item.isBlank()) {
                continue;
            }
            materials.add(new SkillUpgradeConfig.MaterialCost(
                    item,
                    intValue(parseInt(map.get("amount"), 1), 1),
                    parseBoolean(map.get("optional"), false),
                    parseBoolean(map.get("protection"), false)
            ));
        }
        return List.copyOf(materials);
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

    private String baseName(File file) {
        String name = file == null ? "" : file.getName();
        int dot = name.lastIndexOf('.');
        return Texts.lower(dot >= 0 ? name.substring(0, dot) : name);
    }

    private String firstNotBlank(String primary, String fallback) {
        return Texts.isNotBlank(primary) ? primary : Texts.toStringSafe(fallback);
    }
}
