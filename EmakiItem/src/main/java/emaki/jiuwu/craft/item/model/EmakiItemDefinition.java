package emaki.jiuwu.craft.item.model;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;

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
        ItemSetMembership setMembership,
        ItemConditions conditions,
        Map<String, List<String>> actions,
        ItemUpdatePolicy updatePolicy,
        RepairConfig repair,
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
        setMembership = setMembership == null ? ItemSetMembership.empty() : setMembership;
        conditions = conditions == null ? ItemConditions.empty() : conditions;
        actions = actions == null ? Map.of() : copyActions(actions);
        updatePolicy = updatePolicy == null ? ItemUpdatePolicy.defaults() : updatePolicy;
        repair = repair == null ? RepairConfig.disabled() : repair;
    }

    public String definitionSignature() {
        Map<String, Object> signatureData = new java.util.LinkedHashMap<>();
        signatureData.put("id", id);
        signatureData.put("material", material == null ? "" : material.name());
        signatureData.put("display_name", displayName);
        signatureData.put("item_name", itemName);
        signatureData.put("lore", lore);
        signatureData.put("variables", variables);
        signatureData.put("components", components);
        signatureData.put("ea_attributes", attributes);
        signatureData.put("ea_attribute_meta", attributeMeta);
        signatureData.put("es_skills", skills);
        signatureData.put("set", Map.of("id", setMembership.setId(), "piece", setMembership.pieceId()));
        signatureData.put("conditions", conditions);
        signatureData.put("actions", actions);
        signatureData.put("update", updatePolicy.signatureData());
        signatureData.put("repair_enabled", repair.enabled());
        return SignatureUtil.stableSignature(signatureData);
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
