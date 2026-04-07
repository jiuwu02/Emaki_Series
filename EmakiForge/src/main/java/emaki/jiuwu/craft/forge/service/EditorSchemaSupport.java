package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;

final class EditorSchemaSupport {

    private EditorSchemaSupport() {
    }

    static Object defaultListEntry(EditableResourceType type, List<String> path) {
        String normalized = normalizePath(path);
        return switch (type) {
            case BLUEPRINT -> "";
            case MATERIAL -> switch (normalized) {
                case "description" -> "";
                case "effects" -> defaultMaterialEffect();
                default -> "";
            };
            case RECIPE -> switch (normalized) {
                case "blueprint_requirements" -> defaultBlueprintRequirement();
                case "blueprint_requirements.blueprint_options" -> defaultBlueprintOption();
                case "required_materials" -> defaultRequiredMaterial();
                case "optional_materials.whitelist", "optional_materials.blacklist", "conditions", "quality.custom_pool",
                        "result.action", "action.pre", "action.success", "action.failure" -> "";
                case "result.meta_actions.name_modifications" -> defaultNameAction();
                case "result.meta_actions.lore_actions" -> defaultLoreAction();
                default -> "";
            };
        };
    }

    static boolean isSlotListPath(EditableResourceType type, List<String> path) {
        return type == EditableResourceType.RECIPE
                && path != null
                && path.size() >= 3
                && "gui".equals(path.get(0))
                && "slots".equals(path.get(1));
    }

    static boolean isItemSourcePath(List<String> path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String leaf = path.get(path.size() - 1);
        return "item".equals(leaf) || "output_item".equals(leaf);
    }

    static String summarize(Object value) {
        if (value instanceof Map<?, ?> map) {
            return "Map(" + map.size() + ")";
        }
        if (value instanceof List<?> list) {
            return "List(" + list.size() + ")";
        }
        return Texts.toStringSafe(value);
    }

    private static String normalizePath(List<String> path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String segment : path) {
            if (segment == null || segment.startsWith("#")) {
                continue;
            }
            parts.add(segment);
        }
        return String.join(".", parts);
    }

    private static Map<String, Object> defaultMaterialEffect() {
        Map<String, Object> effect = new LinkedHashMap<>();
        effect.put("type", "stat_contribution");
        effect.put("stats", new LinkedHashMap<String, Object>());
        return effect;
    }

    private static Map<String, Object> defaultBlueprintRequirement() {
        Map<String, Object> requirement = new LinkedHashMap<>();
        requirement.put("requirement_mode", "any_of");
        requirement.put("blueprint_options", new ArrayList<>(List.of(defaultBlueprintOption())));
        return requirement;
    }

    private static Map<String, Object> defaultBlueprintOption() {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("selector", new LinkedHashMap<>(Map.of("kind", "id", "value", "")));
        option.put("count", "1");
        return option;
    }

    private static Map<String, Object> defaultRequiredMaterial() {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("id", "");
        material.put("count", "1");
        return material;
    }

    private static Map<String, Object> defaultNameAction() {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("action", "replace");
        action.put("value", "");
        return action;
    }

    private static Map<String, Object> defaultLoreAction() {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("action", "append");
        action.put("content", new ArrayList<String>());
        return action;
    }
}
