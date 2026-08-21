package emaki.jiuwu.craft.forge.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;

public final class Recipe {
    public record QualityConfig(boolean enabled,
            List<String> customPool,
            boolean guaranteeEnabled,
            int guaranteeAttempts,
            String guaranteeMinimum) {

        public static QualityConfig defaults() {
            return new QualityConfig(false, List.of(), false, 10, "普通");
        }
    }

    public record ResultConfig(ItemSourceRef outputItem,
            List<String> action,
            List<Map<String, Object>> nameModifications,
            List<Map<String, Object>> loreActions) {

    }

    public record ActionPhases(List<String> pre, List<String> success, List<String> failure) {

        public static ActionPhases empty() {
            return new ActionPhases(List.of(), List.of(), List.of());
        }
    }

    public record FailureOutcome(String type, int weight, Map<String, Object> params) {

        public FailureOutcome {
            type = Texts.isBlank(type) ? "return_materials" : Texts.lower(type);
            weight = Math.max(1, weight);
            params = params == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(params));
        }
    }

    private final String id;
    private final String displayName;
    private final String guiTemplate;
    private final List<BlueprintRequirement> blueprintRequirements;
    private final List<ForgeMaterial> materials;
    private final int forgeCapacity;
    private final int optionalMaterialLimit;
    private final String conditionType;
    private final int conditionRequiredCount;
    private final ConditionGroup conditions;
    private final QualityConfig quality;
    private final ResultConfig result;
    private final ActionPhases action;
    private final String permission;
    private final double successRate;
    private final List<FailureOutcome> failureOutcomes;

    public Recipe(String id,
            String displayName,
            List<BlueprintRequirement> blueprintRequirements,
            List<ForgeMaterial> materials,
            int forgeCapacity,
            int optionalMaterialLimit,
            String conditionType,
            int conditionRequiredCount,
            ConditionGroup conditions,
            QualityConfig quality,
            ResultConfig result,
            ActionPhases action,
            String permission) {
        this(id, displayName, null, blueprintRequirements, materials, forgeCapacity, optionalMaterialLimit,
                conditionType, conditionRequiredCount, conditions, quality, result, action, permission, 100D, List.of());
    }

    public Recipe(String id,
            String displayName,
            List<BlueprintRequirement> blueprintRequirements,
            List<ForgeMaterial> materials,
            int forgeCapacity,
            int optionalMaterialLimit,
            String conditionType,
            int conditionRequiredCount,
            ConditionGroup conditions,
            QualityConfig quality,
            ResultConfig result,
            ActionPhases action,
            String permission,
            double successRate,
            List<FailureOutcome> failureOutcomes) {
        this(id, displayName, null, blueprintRequirements, materials, forgeCapacity, optionalMaterialLimit,
                conditionType, conditionRequiredCount, conditions, quality, result, action, permission, successRate, failureOutcomes);
    }

    public Recipe(String id,
            String displayName,
            String guiTemplate,
            List<BlueprintRequirement> blueprintRequirements,
            List<ForgeMaterial> materials,
            int forgeCapacity,
            int optionalMaterialLimit,
            String conditionType,
            int conditionRequiredCount,
            ConditionGroup conditions,
            QualityConfig quality,
            ResultConfig result,
            ActionPhases action,
            String permission,
            double successRate,
            List<FailureOutcome> failureOutcomes) {
        this.id = id;
        this.displayName = displayName;
        this.guiTemplate = Texts.isBlank(guiTemplate) ? null : guiTemplate;
        this.blueprintRequirements = List.copyOf(blueprintRequirements);
        this.materials = List.copyOf(materials);
        this.forgeCapacity = forgeCapacity;
        this.optionalMaterialLimit = optionalMaterialLimit;
        this.conditionType = conditionType;
        this.conditionRequiredCount = conditionRequiredCount;
        this.conditions = conditions == null ? ConditionGroup.empty() : conditions;
        this.quality = quality;
        this.result = result;
        this.action = action == null ? ActionPhases.empty() : action;
        this.permission = permission;
        this.successRate = Math.max(0D, Math.min(100D, successRate));
        this.failureOutcomes = failureOutcomes == null ? List.of() : List.copyOf(failureOutcomes);
    }

    public static Recipe fromConfig(YamlSection section) {
        if (section == null) {
            return null;
        }
        String id = section.getString("id");
        if (Texts.isBlank(id)) {
            return null;
        }
        List<BlueprintRequirement> blueprintRequirements = parseBlueprintRequirements(section.get("blueprint_requirements"));
        if (blueprintRequirements == null) {
            return null;
        }
        List<ForgeMaterial> materials = parseMaterials(section.get("materials"));
        if (materials == null) {
            return null;
        }
        ResultConfig result = parseResult(section.get("result"));
        if (result == null) {
            return null;
        }
        ConditionBlock condition = ConditionBlock.fromRoot(section, true, false);
        ConditionGroup conditionGroup = condition.group();
        return new Recipe(
                id,
                section.getString("display_name", id),
                section.getString("gui_template"),
                blueprintRequirements,
                materials,
                Math.max(0, Numbers.tryParseInt(section.get("forge_capacity"), 0)),
                Math.max(0, Numbers.tryParseInt(section.get("optional_material_limit"), 0)),
                conditionGroup.conditionType(),
                conditionGroup.requiredCount(),
                conditionGroup,
                parseQuality(section.get("quality")),
                result,
                parseAction(ConfigNodes.get(section, "actions")),
                section.getString("permission"),
                Numbers.tryParseDouble(section.get("success_rate"), 100D),
                parseFailureOutcomes(section.get("failure_outcomes"))
        );
    }

    private static List<BlueprintRequirement> parseBlueprintRequirements(Object raw) {
        List<BlueprintRequirement> result = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            BlueprintRequirement requirement = BlueprintRequirement.fromConfig(entry);
            if (requirement == null) {
                return null;
            }
            result.add(requirement);
        }
        return result;
    }

    private static List<ForgeMaterial> parseMaterials(Object raw) {
        List<ForgeMaterial> result = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            ForgeMaterial material = ForgeMaterial.fromConfig(entry);
            if (material == null) {
                return null;
            }
            result.add(material);
        }
        return result;
    }

    private static QualityConfig parseQuality(Object raw) {
        if (raw == null) {
            return QualityConfig.defaults();
        }
        Object guarantee = ConfigNodes.get(raw, "guarantee");
        return new QualityConfig(
                ConfigNodes.bool(raw, "enabled", false),
                Texts.asStringList(ConfigNodes.get(raw, "custom_pool")),
                ConfigNodes.bool(guarantee, "enabled", false),
                Numbers.tryParseInt(ConfigNodes.get(guarantee, "attempts"), 10),
                ConfigNodes.string(guarantee, "minimum", "普通")
        );
    }

    private static ResultConfig parseResult(Object raw) {
        Object success = ConfigNodes.get(raw, "success");
        if (success == null) {
            return new ResultConfig(null, List.of(), List.of(), List.of());
        }
        Object outputItem = firstResultOutput(ConfigNodes.get(success, "outputs"));
        ItemSourceRef parsedOutputItem = ItemSourceUtil.parse(outputItem);
        if (outputItem != null && parsedOutputItem == null) {
            return null;
        }
        return new ResultConfig(
                parsedOutputItem,
                List.copyOf(Texts.asStringList(ConfigNodes.get(success, "actions"))),
                toActionList(ConfigNodes.get(success, "name_actions")),
                toActionList(ConfigNodes.get(success, "lore_actions"))
        );
    }

    private static Object firstResultOutput(Object rawOutputs) {
        for (Object entry : ConfigNodes.asObjectList(rawOutputs)) {
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    private static ActionPhases parseAction(Object raw) {
        YamlSection section = raw instanceof YamlSection yamlSection
                ? yamlSection
                : raw instanceof Map<?, ?> map
                ? new MapYamlSection(MapYamlSection.normalizeMap(map))
                : null;
        if (section == null) {
            return ActionPhases.empty();
        }
        return new ActionPhases(
                List.copyOf(section.getStringList("pre")),
                List.copyOf(section.getStringList("success")),
                List.copyOf(section.getStringList("failure"))
        );
    }

    private static List<Map<String, Object>> toActionList(Object... values) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value : values) {
            for (Object entry : ConfigNodes.asObjectList(value)) {
                Object plain = ConfigNodes.toPlainData(entry);
                if (!(plain instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> mapEntry : map.entrySet()) {
                    normalized.put(String.valueOf(mapEntry.getKey()), mapEntry.getValue());
                }
                result.add(normalized);
            }
        }
        return result;
    }

    private static List<FailureOutcome> parseFailureOutcomes(Object raw) {
        List<FailureOutcome> outcomes = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            if (entry == null) {
                continue;
            }
            String type = ConfigNodes.string(entry, "type", "return_materials");
            int weight = Numbers.tryParseInt(ConfigNodes.get(entry, "weight"), 1);
            Map<String, Object> params = new LinkedHashMap<>();
            Object rawParams = ConfigNodes.get(entry, "params");
            if (rawParams instanceof Map<?, ?> paramsMap) {
                for (Map.Entry<?, ?> paramEntry : paramsMap.entrySet()) {
                    params.put(String.valueOf(paramEntry.getKey()), paramEntry.getValue());
                }
            }
            outcomes.add(new FailureOutcome(type, weight, params));
        }
        return outcomes;
    }

    public ForgeMaterial findMaterialBySource(ItemSourceRef source) {
        if (source == null) {
            return null;
        }
        for (ForgeMaterial material : materials) {
            if (material.matches(source)) {
                return material;
            }
        }
        return null;
    }

    public ForgeMaterial findMaterialBySource(ItemSourceRef source, boolean optional) {
        if (source == null) {
            return null;
        }
        for (ForgeMaterial material : materials) {
            if (material.optional() == optional && material.matches(source)) {
                return material;
            }
        }
        return null;
    }

    public ForgeMaterial findMaterialMatching(MatchContext context) {
        if (context == null) {
            return null;
        }
        for (ForgeMaterial material : materials) {
            if (material.matches(context)) {
                return material;
            }
        }
        return null;
    }

    public ForgeMaterial findMaterialMatching(MatchContext context, boolean optional) {
        if (context == null) {
            return null;
        }
        for (ForgeMaterial material : materials) {
            if (material.optional() == optional && material.matches(context)) {
                return material;
            }
        }
        return null;
    }

    public BlueprintRequirement findBlueprintRequirementMatching(MatchContext context) {
        if (context == null) {
            return null;
        }
        for (BlueprintRequirement requirement : blueprintRequirements) {
            if (requirement != null && requirement.matches(context)) {
                return requirement;
            }
        }
        return null;
    }

    public ForgeMaterial findMaterialByItem(String item) {
        if (Texts.isBlank(item)) {
            return null;
        }
        String normalized = Texts.lower(item);
        for (ForgeMaterial material : materials) {
            if (normalized.equals(material.key())) {
                return material;
            }
        }
        return null;
    }

    public List<ForgeMaterial> requiredMaterials() {
        List<ForgeMaterial> result = new ArrayList<>();
        for (ForgeMaterial material : materials) {
            if (material != null && !material.optional()) {
                result.add(material);
            }
        }
        return result;
    }

    public List<ForgeMaterial> optionalMaterials() {
        List<ForgeMaterial> result = new ArrayList<>();
        for (ForgeMaterial material : materials) {
            if (material != null && material.optional()) {
                result.add(material);
            }
        }
        return result;
    }

    public boolean requiresPermission() {
        return Texts.isNotBlank(permission);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String guiTemplate() {
        return guiTemplate;
    }

    public int forgeCapacity() {
        return forgeCapacity;
    }

    public ItemSourceRef configuredOutputSource() {
        return result == null ? null : result.outputItem();
    }

    public boolean requiresTargetInput() {
        return configuredOutputSource() == null;
    }

    public List<BlueprintRequirement> blueprintRequirements() {
        return blueprintRequirements;
    }

    public List<ForgeMaterial> materials() {
        return materials;
    }

    public int optionalMaterialLimit() {
        return optionalMaterialLimit;
    }

    public String conditionType() {
        return conditionType;
    }

    public int conditionRequiredCount() {
        return conditionRequiredCount;
    }

    public ConditionGroup conditions() {
        return conditions;
    }

    public QualityConfig quality() {
        return quality;
    }

    public ResultConfig result() {
        return result;
    }

    public ActionPhases action() {
        return action;
    }

    public String permission() {
        return permission;
    }

    public double successRate() {
        return successRate;
    }

    public List<FailureOutcome> failureOutcomes() {
        return failureOutcomes;
    }

    public boolean hasFailureMechanism() {
        return successRate < 100D;
    }
}
