package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public enum EditableResourceType {

    BLUEPRINT("blueprint", "图纸", "blueprints"),
    MATERIAL("material", "材料", "materials"),
    RECIPE("recipe", "配方", "recipes");

    private final String id;
    private final String displayName;
    private final String directoryName;

    EditableResourceType(String id, String displayName, String directoryName) {
        this.id = id;
        this.displayName = displayName;
        this.directoryName = directoryName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String directoryName() {
        return directoryName;
    }

    public String defaultId() {
        return "new_" + id;
    }

    public String fileName(String resourceId) {
        String effectiveId = resourceId == null || resourceId.isBlank() ? defaultId() : resourceId.trim();
        return effectiveId + ".yml";
    }

    public Map<String, Object> defaultData() {
        return switch (this) {
            case BLUEPRINT -> defaultBlueprint();
            case MATERIAL -> defaultMaterial();
            case RECIPE -> defaultRecipe();
        };
    }

    public static EditableResourceType fromInput(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String normalized = input.trim().toLowerCase();
        return switch (normalized) {
            case "blueprint", "blueprints" -> BLUEPRINT;
            case "material", "materials" -> MATERIAL;
            case "recipe", "recipes" -> RECIPE;
            default -> null;
        };
    }

    private static Map<String, Object> defaultBlueprint() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", "new_blueprint");
        data.put("display_name", "<white>新图纸</white>");
        data.put("description", new ArrayList<String>());
        data.put("item", "paper");
        data.put("tags", new ArrayList<String>());
        data.put("forge_capacity", "0");
        return data;
    }

    private static Map<String, Object> defaultMaterial() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", "new_material");
        data.put("display_name", "<white>新材料</white>");
        data.put("description", new ArrayList<String>());
        data.put("item", "stick");
        data.put("capacity_cost", "0");
        data.put("priority", "0");
        data.put("effects", new ArrayList<Object>());
        return data;
    }

    private static Map<String, Object> defaultRecipe() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", "new_recipe");
        data.put("display_name", "<white>新配方</white>");
        Map<String, Object> targetItem = new LinkedHashMap<>();
        targetItem.put("item", "minecraft:stone");
        targetItem.put("forge_capacity", "0");
        data.put("target_item", targetItem);
        data.put("blueprint_requirements", new ArrayList<Object>());
        data.put("materials", new ArrayList<Object>());
        data.put("optional_material_limit", "0");
        data.put("condition_type", "all_of");
        data.put("condition_required_count", "0");
        data.put("conditions", new ArrayList<String>());
        Map<String, Object> guarantee = new LinkedHashMap<>();
        guarantee.put("enabled", "false");
        guarantee.put("attempts", "10");
        guarantee.put("minimum", "普通");
        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("enabled", "false");
        quality.put("custom_pool", new ArrayList<String>());
        quality.put("guarantee", guarantee);
        data.put("quality", quality);
        Map<String, Object> metaActions = new LinkedHashMap<>();
        metaActions.put("name_modifications", new ArrayList<Object>());
        metaActions.put("lore_actions", new ArrayList<Object>());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output_item", "");
        result.put("meta_actions", metaActions);
        result.put("action", new ArrayList<String>());
        data.put("result", result);
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("pre", new ArrayList<String>());
        action.put("success", new ArrayList<String>());
        action.put("failure", new ArrayList<String>());
        data.put("action", action);
        data.put("permission", "");
        return data;
    }
}
