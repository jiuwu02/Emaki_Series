package emaki.jiuwu.craft.item.service;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.integration.SkillPdcGateway;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;

public final class EmakiItemPdcWriter {

    private static final String ATTRIBUTE_SOURCE_ID = "emakiitem";
    public static final String SET_ATTRIBUTE_SOURCE_ID = "emakiitem_set";

    private final EmakiItemIdentifier identifier;
    private final PdcAttributeGateway attributeGateway;
    private final SkillPdcGateway skillPdcGateway;

    public EmakiItemPdcWriter(EmakiItemIdentifier identifier,
            PdcAttributeGateway attributeGateway,
            SkillPdcGateway skillPdcGateway) {
        this.identifier = identifier;
        this.attributeGateway = attributeGateway;
        this.skillPdcGateway = skillPdcGateway;
    }

    public void write(ItemStack itemStack, EmakiItemDefinition definition) {
        write(itemStack, definition, definition == null ? Map.of() : definition.variables());
    }

    public void write(ItemStack itemStack, EmakiItemDefinition definition, Map<String, ?> variables) {
        if (itemStack == null || definition == null) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            Integer updateVersion = definition.updatePolicy().updateEnabled() ? definition.updatePolicy().version() : null;
            identifier.writeIdentity(itemMeta, definition.id(), definition.definitionSignature(), updateVersion);
            itemStack.setItemMeta(itemMeta);
        }
        Map<String, Double> attributes = resolveAttributes(definition.attributes());
        if (!attributes.isEmpty()
                && Bukkit.getPluginManager().isPluginEnabled("EmakiAttribute")) {
            attributeGateway.write(itemStack, ATTRIBUTE_SOURCE_ID, attributes, Map.of());
        }
        if (!definition.skills().isEmpty()
                && Bukkit.getPluginManager().isPluginEnabled("EmakiSkills")) {
            skillPdcGateway.write(itemStack, definition.skills());
        }
    }

    public void writeDynamicSet(ItemStack itemStack,
            EmakiItemDefinition definition,
            String setId,
            String setPiece,
            int activeCount,
            int totalCount,
            List<Integer> activeThresholds,
            int setLoreLines,
            Map<String, Double> setAttributes,
            List<String> setSkills,
            String setSignature) {
        if (itemStack == null || definition == null) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            identifier.writeSetState(itemMeta, setId, setPiece, activeCount, totalCount, thresholds(activeThresholds), setLoreLines, setSignature);
            itemStack.setItemMeta(itemMeta);
        }
        if (Bukkit.getPluginManager().isPluginEnabled("EmakiAttribute")) {
            if (setAttributes == null || setAttributes.isEmpty()) {
                attributeGateway.clear(itemStack, SET_ATTRIBUTE_SOURCE_ID);
            } else {
                attributeGateway.write(itemStack, SET_ATTRIBUTE_SOURCE_ID, setAttributes, Map.of(
                        "set_id", Texts.normalizeId(setId),
                        "active_count", Integer.toString(Math.max(0, activeCount)),
                        "active_thresholds", thresholds(activeThresholds)
                ));
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("EmakiSkills")) {
            java.util.LinkedHashSet<String> skills = new java.util.LinkedHashSet<>(definition.skills());
            if (setSkills != null) {
                skills.addAll(setSkills);
            }
            skillPdcGateway.write(itemStack, skills);
        }
    }

    public void clearDynamicSet(ItemStack itemStack, EmakiItemDefinition definition) {
        if (itemStack == null) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            identifier.clearSetState(itemMeta);
            itemStack.setItemMeta(itemMeta);
        }
        attributeGateway.clear(itemStack, SET_ATTRIBUTE_SOURCE_ID);
        if (definition != null && Bukkit.getPluginManager().isPluginEnabled("EmakiSkills")) {
            skillPdcGateway.write(itemStack, definition.skills());
        }
    }

    public void shutdown() {
        attributeGateway.shutdown();
    }

    private String thresholds(List<Integer> thresholds) {
        if (thresholds == null || thresholds.isEmpty()) {
            return "";
        }
        return thresholds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(";"));
    }

    private Map<String, Double> resolveAttributes(Map<String, Object> rawAttributes) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (rawAttributes == null || rawAttributes.isEmpty()) {
            return result;
        }
        Map<String, Object> context = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawAttributes.entrySet()) {
            if (Texts.isBlank(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            Double value = resolveAttributeValue(entry.getValue(), context);
            if (value != null) {
                String key = Texts.normalizeId(entry.getKey());
                result.put(key, value);
                context.put(key, value);
            }
        }
        return result;
    }

    private Double resolveAttributeValue(Object raw, Map<String, ?> variables) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof Map<?, ?>) {
            return ExpressionEngine.evaluateRandomConfig(raw, variables);
        }
        String evaluated = ExpressionEngine.evaluateStringConfig(raw, variables);
        return Numbers.tryParseDouble(evaluated, null);
    }

}
