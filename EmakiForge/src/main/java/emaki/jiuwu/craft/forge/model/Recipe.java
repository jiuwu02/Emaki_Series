package emaki.jiuwu.craft.forge.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

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

    public record ResultConfig(ItemSource outputItem,
            List<String> action,
            List<Map<String, Object>> nameModifications,
            List<Map<String, Object>> loreActions,
            Object structuredPresentation) {

    }

    public record ActionPhases(List<String> pre, List<String> success, List<String> failure) {

        public static ActionPhases empty() {
            return new ActionPhases(List.of(), List.of(), List.of());
        }
    }

    private final String id;
    private final String displayName;
    private final List<BlueprintRequirement> blueprintRequirements;
    private final List<ForgeMaterial> materials;
    private final int forgeCapacity;
    private final int optionalMaterialLimit;
    private final String conditionType;
    private final int conditionRequiredCount;
    private final List<String> conditions;
    private final QualityConfig quality;
    private final ResultConfig result;
    private final ActionPhases action;
    private final String permission;

    public Recipe(String id,
            String displayName,
            List<BlueprintRequirement> blueprintRequirements,
            List<ForgeMaterial> materials,
            int forgeCapacity,
            int optionalMaterialLimit,
            String conditionType,
            int conditionRequiredCount,
            List<String> conditions,
            QualityConfig quality,
            ResultConfig result,
            ActionPhases action,
            String permission) {
        this.id = id;
        this.displayName = displayName;
        this.blueprintRequirements = List.copyOf(blueprintRequirements);
        this.materials = List.copyOf(materials);
        this.forgeCapacity = forgeCapacity;
        this.optionalMaterialLimit = optionalMaterialLimit;
        this.conditionType = conditionType;
        this.conditionRequiredCount = conditionRequiredCount;
        this.conditions = List.copyOf(conditions);
        this.quality = quality;
        this.result = result;
        this.action = action == null ? ActionPhases.empty() : action;
        this.permission = permission;
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
        return new Recipe(
                id,
                section.getString("display_name", id),
                blueprintRequirements,
                materials,
                Math.max(0, Numbers.tryParseInt(section.get("forge_capacity"), 0)),
                Math.max(0, Numbers.tryParseInt(section.get("optional_material_limit"), 0)),
                section.getString("condition_type", "all_of"),
                Numbers.tryParseInt(section.get("condition_required_count"), 0),
                Texts.asStringList(section.get("conditions")),
                parseQuality(section.get("quality")),
                result,
                parseAction(ConfigNodes.get(section, "action")),
                section.getString("permission")
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
        if (raw == null) {
            return new ResultConfig(null, List.of(), List.of(), List.of(), null);
        }
        Object outputItem = ConfigNodes.get(raw, "output_item");
        ItemSource parsedOutputItem = ItemSourceUtil.parse(outputItem);
        if (Texts.isNotBlank(Texts.toStringSafe(outputItem)) && parsedOutputItem == null) {
            return null;
        }
        Object metaActions = ConfigNodes.get(raw, "meta_actions");
        return new ResultConfig(
                parsedOutputItem,
                List.copyOf(Texts.asStringList(ConfigNodes.get(raw, "action"))),
                toActionList(ConfigNodes.get(metaActions, "name_modifications")),
                toActionList(ConfigNodes.get(metaActions, "lore_actions")),
                ConfigNodes.toPlainData(ConfigNodes.get(metaActions, "structured_presentation"))
        );
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

    public ForgeMaterial findMaterialBySource(ItemSource source) {
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

    public ForgeMaterial findMaterialBySource(ItemSource source, boolean optional) {
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

    public int forgeCapacity() {
        return forgeCapacity;
    }

    public ItemSource configuredOutputSource() {
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

    public List<String> conditions() {
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
}
