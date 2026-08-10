package emaki.jiuwu.craft.item.service;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.integration.ItemAttributeBridge;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPayload;
import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPdcCodec;
import emaki.jiuwu.craft.skills.api.pdc.SkillPdcMutation;

public final class EmakiItemPdcWriter {

    private static final String ATTRIBUTE_SOURCE_ID = "emakiitem";
    public static final String SET_ATTRIBUTE_SOURCE_ID = "emakiitem_set";

    private final EmakiItemIdentifier identifier;
    private final ItemAttributeBridge attributeGateway;
    private final DebugLogger debugLogger;

    public EmakiItemPdcWriter(EmakiItemIdentifier identifier,
            ItemAttributeBridge attributeGateway,
            DebugLogger debugLogger) {
        this.identifier = identifier;
        this.attributeGateway = attributeGateway;
        this.debugLogger = debugLogger;
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
        String equipSlot = EquipmentSkillPdcCodec.normalizeRequiredSlot(definition.equipSlot());
        Map<String, Double> attributes = resolveAttributes(definition.attributes());
        if (!attributes.isEmpty()) {
            attributeGateway.write(itemStack, ATTRIBUTE_SOURCE_ID, attributes, Map.of(
                    EquipmentSlotMatcher.ACTIVE_SLOT_META_KEY, equipSlot
            ));
        }
        observeSkillMutation(itemStack, EquipmentSkillPdcCodec.write(
                itemStack,
                definition.skills(),
                equipSlot,
                definition.skillTriggers()
        ));
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
        if (setAttributes == null || setAttributes.isEmpty()) {
            attributeGateway.clear(itemStack, SET_ATTRIBUTE_SOURCE_ID);
        } else {
            attributeGateway.write(itemStack, SET_ATTRIBUTE_SOURCE_ID, setAttributes, Map.of(
                    "set_id", Texts.normalizeId(setId),
                    "active_count", Integer.toString(Math.max(0, activeCount)),
                    "active_thresholds", thresholds(activeThresholds)
            ));
        }
        LinkedHashSet<String> skills = new LinkedHashSet<>(definition.skills());
        if (setSkills != null) {
            skills.addAll(setSkills);
        }
        observeSkillMutation(itemStack, EquipmentSkillPdcCodec.write(
                itemStack,
                skills,
                EquipmentSkillPdcCodec.normalizeRequiredSlot(definition.equipSlot()),
                definition.skillTriggers()
        ));
    }

    boolean isDynamicSetCurrent(ItemStack itemStack,
            EmakiItemDefinition definition,
            EmakiItemIdentifier.Snapshot identity,
            String setId,
            String setPiece,
            int activeCount,
            int totalCount,
            List<Integer> activeThresholds,
            Map<String, Double> setAttributes,
            List<String> setSkills,
            String setSignature) {
        if (itemStack == null || definition == null || identity == null || !identity.completeSetState()) {
            return false;
        }
        String expectedThresholds = thresholds(activeThresholds);
        if (!Texts.normalizeId(setId).equals(identity.setId())
                || !Texts.normalizeId(setPiece).equals(identity.setPiece())
                || identity.setActiveCount() == null
                || identity.setActiveCount() != Math.max(0, activeCount)
                || identity.setTotalCount() == null
                || identity.setTotalCount() != Math.max(0, totalCount)
                || !expectedThresholds.equals(identity.setActiveThresholds())
                || !Texts.toStringSafe(setSignature).equals(identity.setSignature())) {
            return false;
        }
        if (attributeGateway.available()
                && !isSetAttributePayloadCurrent(itemStack, setId, activeCount, expectedThresholds, setAttributes)) {
            return false;
        }
        return isSetSkillPayloadCurrent(itemStack, definition, setSkills);
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
        SkillPdcMutation mutation = definition == null
                ? EquipmentSkillPdcCodec.clear(itemStack)
                : EquipmentSkillPdcCodec.write(
                        itemStack,
                        definition.skills(),
                        EquipmentSkillPdcCodec.normalizeRequiredSlot(definition.equipSlot()),
                        definition.skillTriggers()
                );
        observeSkillMutation(itemStack, mutation);
    }

    public void shutdown() {
        attributeGateway.shutdown();
    }

    private boolean isSetAttributePayloadCurrent(ItemStack itemStack,
            String setId,
            int activeCount,
            String activeThresholds,
            Map<String, Double> setAttributes) {
        Map<String, Double> expectedAttributes = setAttributes == null || setAttributes.isEmpty()
                ? Map.of()
                : Map.copyOf(setAttributes);
        if (expectedAttributes.isEmpty()) {
            return !attributeGateway.hasPayload(itemStack, SET_ATTRIBUTE_SOURCE_ID);
        }
        if (!attributeGateway.hasPayload(itemStack, SET_ATTRIBUTE_SOURCE_ID)) {
            return false;
        }
        Map<String, String> expectedMeta = Map.of(
                "set_id", Texts.normalizeId(setId),
                "active_count", Integer.toString(Math.max(0, activeCount)),
                "active_thresholds", Texts.toStringSafe(activeThresholds)
        );
        return expectedAttributes.equals(attributeGateway.readAttributes(itemStack, SET_ATTRIBUTE_SOURCE_ID))
                && expectedMeta.equals(attributeGateway.readMeta(itemStack, SET_ATTRIBUTE_SOURCE_ID));
    }

    private boolean isSetSkillPayloadCurrent(ItemStack itemStack,
            EmakiItemDefinition definition,
            List<String> setSkills) {
        LinkedHashSet<String> expectedSkills = new LinkedHashSet<>(definition.skills());
        if (setSkills != null) {
            expectedSkills.addAll(setSkills);
        }
        EquipmentSkillPayload expected = EquipmentSkillPdcCodec.normalize(
                expectedSkills,
                definition.equipSlot(),
                definition.skillTriggers()
        );
        return expected.equals(EquipmentSkillPdcCodec.read(itemStack));
    }

    private void observeSkillMutation(ItemStack itemStack, SkillPdcMutation mutation) {
        if (debugLogger == null
                || mutation == null
                || !debugLogger.shouldLog("pdc", (UUID) null)) {
            return;
        }
        debugLogger.log("pdc", (UUID) null, "pdc.skill_payload", Map.of(
                "operation", mutation.operation(),
                "item", itemStack == null ? "null" : itemStack.getType(),
                "amount", itemStack == null ? 0 : itemStack.getAmount(),
                "before", mutation.before().values(),
                "after", mutation.after().values(),
                "committed", mutation.committed(),
                "reason", mutation.reason()
        ));
    }

    private String thresholds(List<Integer> thresholds) {
        if (thresholds == null || thresholds.isEmpty()) {
            return "";
        }
        return thresholds.stream().map(String::valueOf).collect(Collectors.joining(";"));
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
