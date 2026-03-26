package emaki.jiuwu.craft.forge.model;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.gui.SlotParser;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public final class Recipe {

    public record BlueprintOption(Map<String, Object> selector, int count) {
    }

    public record BlueprintRequirement(String requirementMode, List<BlueprintOption> blueprintOptions) {
    }

    public record RequiredMaterial(String id, int count) {
    }

    public record OptionalMaterialsConfig(boolean enabled, List<String> whitelist, List<String> blacklist, int maxCount) {
        public static OptionalMaterialsConfig defaults() {
            return new OptionalMaterialsConfig(false, List.of(), List.of(), 5);
        }
    }

    public record QualityConfig(boolean enabled,
                                List<String> customPool,
                                boolean guaranteeEnabled,
                                int guaranteeAttempts,
                                String guaranteeMinimum) {
        public static QualityConfig defaults() {
            return new QualityConfig(false, List.of(), false, 10, "普通");
        }
    }

    public record GuiConfig(String template, Map<String, List<Integer>> slots) {
    }

    public record ResultConfig(ItemSource outputItem,
                               List<String> action,
                               List<Map<String, Object>> nameModifications,
                               List<Map<String, Object>> loreActions) {
    }

    public record ActionPhases(List<String> pre, List<String> success, List<String> failure) {
        public static ActionPhases empty() {
            return new ActionPhases(List.of(), List.of(), List.of());
        }
    }

    private final String id;
    private final String displayName;
    private final ItemSource targetItemSource;
    private final int forgeCapacity;
    private final List<BlueprintRequirement> blueprintRequirements;
    private final List<RequiredMaterial> requiredMaterials;
    private final OptionalMaterialsConfig optionalMaterials;
    private final String conditionType;
    private final int conditionRequiredCount;
    private final List<String> conditions;
    private final QualityConfig quality;
    private final GuiConfig gui;
    private final ResultConfig result;
    private final ActionPhases action;
    private final String permission;

    public Recipe(String id,
                  String displayName,
                  ItemSource targetItemSource,
                  int forgeCapacity,
                  List<BlueprintRequirement> blueprintRequirements,
                  List<RequiredMaterial> requiredMaterials,
                  OptionalMaterialsConfig optionalMaterials,
                  String conditionType,
                  int conditionRequiredCount,
                  List<String> conditions,
                  QualityConfig quality,
                  GuiConfig gui,
                  ResultConfig result,
                  ActionPhases action,
                  String permission) {
        this.id = id;
        this.displayName = displayName;
        this.targetItemSource = targetItemSource;
        this.forgeCapacity = forgeCapacity;
        this.blueprintRequirements = List.copyOf(blueprintRequirements);
        this.requiredMaterials = List.copyOf(requiredMaterials);
        this.optionalMaterials = optionalMaterials;
        this.conditionType = conditionType;
        this.conditionRequiredCount = conditionRequiredCount;
        this.conditions = List.copyOf(conditions);
        this.quality = quality;
        this.gui = gui;
        this.result = result;
        this.action = action == null ? ActionPhases.empty() : action;
        this.permission = permission;
    }

    public static Recipe fromConfig(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String id = section.getString("id");
        if (Texts.isBlank(id)) {
            return null;
        }
        ConfigurationSection targetSection = section.getConfigurationSection("target_item");
        ItemSource targetSource = targetSection == null ? null : ItemSourceUtil.parse(targetSection);
        int forgeCapacity = Numbers.tryParseInt(section.get("forge_capacity"), 0);
        if (forgeCapacity <= 0 && targetSection != null) {
            forgeCapacity = Numbers.tryParseInt(targetSection.get("forge_capacity"), 0);
        }
        List<BlueprintRequirement> blueprintRequirements = new ArrayList<>();
        for (Object raw : ConfigNodes.asObjectList(section.get("blueprint_requirements"))) {
            BlueprintRequirement requirement = parseBlueprintRequirement(raw);
            if (requirement != null) {
                blueprintRequirements.add(requirement);
            }
        }
        List<RequiredMaterial> requiredMaterials = new ArrayList<>();
        for (Object raw : ConfigNodes.asObjectList(section.get("required_materials"))) {
            RequiredMaterial material = parseRequiredMaterial(raw);
            if (material != null) {
                requiredMaterials.add(material);
            }
        }
        return new Recipe(
            id,
            section.getString("display_name", id),
            targetSource,
            forgeCapacity,
            blueprintRequirements,
            requiredMaterials,
            parseOptionalMaterials(section.get("optional_materials")),
            section.getString("condition_type", "all_of"),
            Numbers.tryParseInt(section.get("condition_required_count"), 0),
            Texts.asStringList(section.get("conditions")),
            parseQuality(section.get("quality")),
            parseGui(section.get("gui")),
            parseResult(section.get("result")),
            parseAction(ConfigNodes.get(section, "action")),
            section.getString("permission")
        );
    }

    private static BlueprintRequirement parseBlueprintRequirement(Object raw) {
        if (raw == null) {
            return null;
        }
        List<BlueprintOption> options = new ArrayList<>();
        for (Object optionRaw : ConfigNodes.asObjectList(ConfigNodes.get(raw, "blueprint_options"))) {
            Map<String, Object> selector = new LinkedHashMap<>(ConfigNodes.entries(ConfigNodes.get(optionRaw, "selector")));
            if (selector.isEmpty()) {
                continue;
            }
            options.add(new BlueprintOption(selector, Numbers.tryParseInt(ConfigNodes.get(optionRaw, "count"), 1)));
        }
        return new BlueprintRequirement(ConfigNodes.string(raw, "requirement_mode", "all_of"), options);
    }

    private static RequiredMaterial parseRequiredMaterial(Object raw) {
        String id = ConfigNodes.string(raw, "id", null);
        if (Texts.isBlank(id)) {
            return null;
        }
        return new RequiredMaterial(id, Numbers.tryParseInt(ConfigNodes.get(raw, "count"), 1));
    }

    private static OptionalMaterialsConfig parseOptionalMaterials(Object raw) {
        if (raw == null) {
            return OptionalMaterialsConfig.defaults();
        }
        return new OptionalMaterialsConfig(
            ConfigNodes.bool(raw, "enabled", false),
            Texts.asStringList(ConfigNodes.get(raw, "whitelist")),
            Texts.asStringList(ConfigNodes.get(raw, "blacklist")),
            Numbers.tryParseInt(ConfigNodes.get(raw, "max_count"), 5)
        );
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

    private static GuiConfig parseGui(Object raw) {
        if (raw == null) {
            return new GuiConfig(null, Map.of());
        }
        Map<String, List<Integer>> slots = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(ConfigNodes.get(raw, "slots")).entrySet()) {
            Object slotValue = entry.getValue();
            Object nestedSlots = ConfigNodes.get(slotValue, "slots");
            slots.put(entry.getKey(), SlotParser.parse(nestedSlots == null ? slotValue : nestedSlots));
        }
        return new GuiConfig(ConfigNodes.string(raw, "template", null), slots);
    }

    private static ResultConfig parseResult(Object raw) {
        if (raw == null) {
            return new ResultConfig(null, List.of(), List.of(), List.of());
        }
        Object outputItem = ConfigNodes.get(raw, "output_item");
        Object metaActions = ConfigNodes.get(raw, "meta_actions");
        return new ResultConfig(
            ItemSourceUtil.parse(outputItem),
            List.copyOf(Texts.asStringList(ConfigNodes.get(raw, "action"))),
            toActionList(
                ConfigNodes.get(metaActions, "name_modifications"),
                ConfigNodes.get(metaActions, "name_actions"),
                ConfigNodes.get(raw, "name_modifications"),
                ConfigNodes.get(raw, "name_actions")
            ),
            toActionList(ConfigNodes.get(metaActions, "lore_actions"), ConfigNodes.get(raw, "lore_actions"))
        );
    }

    private static ActionPhases parseAction(Object raw) {
        if (!(raw instanceof ConfigurationSection section)) {
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

    public boolean requiresPermission() {
        return Texts.isNotBlank(permission);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public ItemSource targetItemSource() {
        return targetItemSource;
    }

    public int forgeCapacity() {
        return forgeCapacity;
    }

    public List<BlueprintRequirement> blueprintRequirements() {
        return blueprintRequirements;
    }

    public List<RequiredMaterial> requiredMaterials() {
        return requiredMaterials;
    }

    public OptionalMaterialsConfig optionalMaterials() {
        return optionalMaterials;
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

    public GuiConfig gui() {
        return gui;
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
