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
        data.put("target_item", new LinkedHashMap<>(Map.of("item", "stone")));
        data.put("forge_capacity", "0");
        data.put("blueprint_requirements", new ArrayList<Object>());
        data.put("required_materials", new ArrayList<Object>());
        data.put("optional_materials", new LinkedHashMap<>(Map.of(
                "enabled", "false",
                "whitelist", new ArrayList<String>(),
                "blacklist", new ArrayList<String>(),
                "max_count", "0"
        )));
        data.put("condition_type", "all_of");
        data.put("condition_required_count", "0");
        data.put("conditions", new ArrayList<String>());
        data.put("quality", new LinkedHashMap<>(Map.of(
                "enabled", "false",
                "custom_pool", new ArrayList<String>(),
                "guarantee", new LinkedHashMap<>(Map.of(
                        "enabled", "false",
                        "attempts", "10",
                        "minimum", "普通"
                ))
        )));
        data.put("gui", new LinkedHashMap<>(Map.of(
                "template", "forge_gui",
                "slots", new LinkedHashMap<String, Object>()
        )));
        data.put("result", new LinkedHashMap<>(Map.of(
                "output_item", "",
                "meta_actions", new LinkedHashMap<>(Map.of(
                        "name_modifications", new ArrayList<Object>(),
                        "lore_actions", new ArrayList<Object>()
                )),
                "action", new ArrayList<String>()
        )));
        data.put("action", new LinkedHashMap<>(Map.of(
                "pre", new ArrayList<String>(),
                "success", new ArrayList<String>(),
                "failure", new ArrayList<String>()
        )));
        data.put("permission", "");
        return data;
    }
}
