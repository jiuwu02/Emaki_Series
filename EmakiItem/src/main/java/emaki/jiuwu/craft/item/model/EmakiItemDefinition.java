package emaki.jiuwu.craft.item.model;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;

public record EmakiItemDefinition(String id,
        Material material,
        Object displayName,
        String itemName,
        Object lore,
        Map<String, Object> variables,
        ItemComponentsConfig components,
        Map<String, Double> attributes,
        Map<String, String> attributeMeta,
        List<String> skills,
        ItemConditions conditions,
        Map<String, List<String>> actions,
        boolean hasRandomElements) {

    public EmakiItemDefinition {
        id = id == null ? "" : id;
        itemName = itemName == null ? "" : itemName;
        displayName = ConfigNodes.toPlainData(displayName);
        lore = ConfigNodes.toPlainData(lore);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        components = components == null ? ItemComponentsConfig.empty() : components;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        attributeMeta = attributeMeta == null ? Map.of() : Map.copyOf(attributeMeta);
        skills = skills == null ? List.of() : List.copyOf(skills);
        conditions = conditions == null ? ItemConditions.empty() : conditions;
        actions = actions == null ? Map.of() : copyActions(actions);
    }

    public List<String> actions(String trigger) {
        if (trigger == null || trigger.isBlank()) {
            return List.of();
        }
        return actions.getOrDefault(trigger.toLowerCase(), List.of());
    }

    private static Map<String, List<String>> copyActions(Map<String, List<String>> source) {
        java.util.LinkedHashMap<String, List<String>> copy = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null && !value.isEmpty()) {
                copy.put(key.toLowerCase(), List.copyOf(value));
            }
        });
        return Map.copyOf(copy);
    }
}
