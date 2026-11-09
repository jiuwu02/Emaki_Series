package emaki.jiuwu.craft.item.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class EmakiItemDefinition {

    private final String id;
    private final ConfiguredItemDefinition itemDefinition;
    private final Material material;
    private final Object displayName;
    private final String itemName;
    private final Object lore;
    private final Object nameActions;
    private final Object loreActions;
    private final Map<String, Object> variables;
    private final ItemComponentsConfig components;
    private final Map<String, Object> attributes;
    private final List<String> skills;
    private final Map<String, String> skillTriggers;
    private final String equipSlot;
    private final List<String> accessorySlots;
    private final List<Map<String, Object>> customEffects;
    private final ItemSetMembership setMembership;
    private final ItemConditions conditions;
    private final Map<String, List<String>> actions;
    private final ItemUpdatePolicy updatePolicy;
    private final RepairConfig repair;
    private final boolean hasRandomElements;

    public EmakiItemDefinition(String id,
            ConfiguredItemDefinition itemDefinition,
            Object nameActions,
            Object loreActions,
            Map<String, Object> variables,
            Map<String, Object> attributes,
            List<String> skills,
            Map<String, String> skillTriggers,
            String equipSlot,
            List<String> accessorySlots,
            List<Map<String, Object>> customEffects,
            ItemSetMembership setMembership,
            ItemConditions conditions,
            Map<String, List<String>> actions,
            ItemUpdatePolicy updatePolicy,
            RepairConfig repair,
            boolean hasRandomElements) {
        this(new Spec(
                id,
                itemDefinition,
                projectMaterial(itemDefinition),
                componentValue(itemDefinition, "minecraft:custom_name"),
                Texts.toStringSafe(componentValue(itemDefinition, "minecraft:item_name")),
                componentValue(itemDefinition, "minecraft:lore"),
                ItemComponentsConfig.fromDefinition(itemDefinition),
                nameActions,
                loreActions,
                variables,
                attributes,
                skills,
                skillTriggers,
                equipSlot,
                accessorySlots,
                customEffects,
                setMembership,
                conditions,
                actions,
                updatePolicy,
                repair,
                hasRandomElements
        ));
    }

    private record Spec(
            String id,
            ConfiguredItemDefinition itemDefinition,
            Material material,
            Object displayName,
            String itemName,
            Object lore,
            ItemComponentsConfig components,
            Object nameActions,
            Object loreActions,
            Map<String, Object> variables,
            Map<String, Object> attributes,
            List<String> skills,
            Map<String, String> skillTriggers,
            String equipSlot,
            List<String> accessorySlots,
            List<Map<String, Object>> customEffects,
            ItemSetMembership setMembership,
            ItemConditions conditions,
            Map<String, List<String>> actions,
            ItemUpdatePolicy updatePolicy,
            RepairConfig repair,
            boolean hasRandomElements) {
    }

    private EmakiItemDefinition(Spec spec) {
        this.id = spec.id() == null ? "" : spec.id();
        this.itemDefinition = spec.itemDefinition() == null
                ? new ConfiguredItemDefinition(null, 1, Map.of())
                : spec.itemDefinition();
        this.material = spec.material();
        this.displayName = ConfigNodes.toPlainData(spec.displayName());
        this.itemName = spec.itemName() == null ? "" : spec.itemName();
        this.lore = ConfigNodes.toPlainData(spec.lore());
        this.nameActions = ConfigNodes.toPlainData(spec.nameActions());
        this.loreActions = ConfigNodes.toPlainData(spec.loreActions());
        this.variables = spec.variables() == null ? Map.of() : Map.copyOf(spec.variables());
        this.components = spec.components() == null ? ItemComponentsConfig.empty() : spec.components();
        this.attributes = spec.attributes() == null ? Map.of() : Map.copyOf(spec.attributes());
        this.skills = spec.skills() == null ? List.of() : List.copyOf(spec.skills());
        this.skillTriggers = spec.skillTriggers() == null ? Map.of() : Map.copyOf(spec.skillTriggers());
        this.equipSlot = EquipmentSlotMatcher.normalizeRequired(spec.equipSlot());
        this.accessorySlots = spec.accessorySlots() == null ? List.of() : List.copyOf(spec.accessorySlots());
        this.customEffects = spec.customEffects() == null ? List.of() : List.copyOf(spec.customEffects());
        this.setMembership = spec.setMembership() == null ? ItemSetMembership.empty() : spec.setMembership();
        this.conditions = spec.conditions() == null ? ItemConditions.empty() : spec.conditions();
        this.actions = spec.actions() == null ? Map.of() : copyActions(spec.actions());
        this.updatePolicy = spec.updatePolicy() == null ? ItemUpdatePolicy.defaults() : spec.updatePolicy();
        this.repair = spec.repair() == null ? RepairConfig.disabled() : spec.repair();
        this.hasRandomElements = spec.hasRandomElements();
    }

    public String id() {
        return id;
    }

    public ConfiguredItemDefinition itemDefinition() {
        return itemDefinition;
    }

    public Material material() {
        return material;
    }

    public Object displayName() {
        return displayName;
    }

    public String itemName() {
        return itemName;
    }

    public Object lore() {
        return lore;
    }

    public Object nameActions() {
        return nameActions;
    }

    public Object loreActions() {
        return loreActions;
    }

    public Map<String, Object> variables() {
        return variables;
    }

    public ItemComponentsConfig components() {
        return components;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public List<String> skills() {
        return skills;
    }

    public Map<String, String> skillTriggers() {
        return skillTriggers;
    }

    public String equipSlot() {
        return equipSlot;
    }

    public List<String> accessorySlots() {
        return accessorySlots;
    }

    public List<Map<String, Object>> customEffects() {
        return customEffects;
    }

    public ItemSetMembership setMembership() {
        return setMembership;
    }

    public ItemConditions conditions() {
        return conditions;
    }

    public Map<String, List<String>> actions() {
        return actions;
    }

    public ItemUpdatePolicy updatePolicy() {
        return updatePolicy;
    }

    public RepairConfig repair() {
        return repair;
    }

    public boolean hasRandomElements() {
        return hasRandomElements;
    }

    public String definitionSignature() {
        Map<String, Object> signatureData = new LinkedHashMap<>();
        signatureData.put("id", id);
        signatureData.put("item", normalizedItemSnapshot());
        signatureData.put("name_actions", nameActions);
        signatureData.put("lore_actions", loreActions);
        signatureData.put("variables", variables);
        signatureData.put("ea_attributes", attributes);
        signatureData.put("es_skills", skills);
        signatureData.put("es_skill_triggers", skillTriggers);
        signatureData.put("equip_slot", equipSlot);
        signatureData.put("accessory_slots", accessorySlots);
        if (!customEffects.isEmpty()) {
            signatureData.put("custom_effects", customEffects);
        }
        signatureData.put("set", Map.of("id", setMembership.setId(), "piece", setMembership.pieceId()));
        signatureData.put("conditions", conditions);
        signatureData.put("actions", actions);
        signatureData.put("update", updatePolicy.signatureData());
        signatureData.put("repair_enabled", repair.enabled());
        return SignatureUtil.stableSignature(signatureData);
    }

    public Map<String, Object> normalizedItemSnapshot() {
        Map<String, Object> componentSnapshot = new LinkedHashMap<>();
        itemDefinition.components().forEach((componentId, patch) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("operation", patch.operation().name().toLowerCase(Locale.ROOT));
            if (patch.operation() == ItemComponentPatch.Operation.SET) {
                value.put("value", patch.value());
            }
            componentSnapshot.put(componentId, Collections.unmodifiableMap(value));
        });
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source", Texts.toStringSafe(itemDefinition.source()));
        snapshot.put("components", Collections.unmodifiableMap(componentSnapshot));
        return Collections.unmodifiableMap(snapshot);
    }

    public List<String> actions(String trigger) {
        if (trigger == null || trigger.isBlank()) {
            return List.of();
        }
        return actions.getOrDefault(trigger.toLowerCase(Locale.ROOT), List.of());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EmakiItemDefinition definition)) {
            return false;
        }
        return hasRandomElements == definition.hasRandomElements
                && id.equals(definition.id)
                && itemDefinition.equals(definition.itemDefinition)
                && material == definition.material
                && Objects.equals(displayName, definition.displayName)
                && itemName.equals(definition.itemName)
                && Objects.equals(lore, definition.lore)
                && Objects.equals(nameActions, definition.nameActions)
                && Objects.equals(loreActions, definition.loreActions)
                && variables.equals(definition.variables)
                && components.equals(definition.components)
                && attributes.equals(definition.attributes)
                && skills.equals(definition.skills)
                && skillTriggers.equals(definition.skillTriggers)
                && equipSlot.equals(definition.equipSlot)
                && accessorySlots.equals(definition.accessorySlots)
                && customEffects.equals(definition.customEffects)
                && setMembership.equals(definition.setMembership)
                && conditions.equals(definition.conditions)
                && actions.equals(definition.actions)
                && updatePolicy.equals(definition.updatePolicy)
                && repair.equals(definition.repair);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, itemDefinition, material, displayName, itemName, lore, nameActions, loreActions,
                variables, components, attributes, skills, skillTriggers, equipSlot, accessorySlots, customEffects,
                setMembership, conditions, actions, updatePolicy, repair, hasRandomElements);
    }

    @Override
    public String toString() {
        return "EmakiItemDefinition[id=" + id
                + ", material=" + material
                + ", displayName=" + displayName
                + ", itemName=" + itemName
                + ", lore=" + lore
                + ", nameActions=" + nameActions
                + ", loreActions=" + loreActions
                + ", variables=" + variables
                + ", components=" + components
                + ", attributes=" + attributes
                + ", skills=" + skills
                + ", skillTriggers=" + skillTriggers
                + ", equipSlot=" + equipSlot
                + ", accessorySlots=" + accessorySlots
                + ", customEffects=" + customEffects
                + ", setMembership=" + setMembership
                + ", conditions=" + conditions
                + ", actions=" + actions
                + ", updatePolicy=" + updatePolicy
                + ", repair=" + repair
                + ", hasRandomElements=" + hasRandomElements + "]";
    }

    private static Material projectMaterial(ConfiguredItemDefinition definition) {
        ItemSourceRef source = definition == null ? null : ItemSourceUtil.parse(definition.source());
        return source == null || !source.vanilla()
                ? null
                : ItemSourceUtil.resolveVanillaMaterial(source.identifier());
    }

    private static Object componentValue(ConfiguredItemDefinition definition, String componentId) {
        if (definition == null) {
            return null;
        }
        ItemComponentPatch patch = definition.components().get(componentId);
        return patch == null || patch.operation() != ItemComponentPatch.Operation.SET ? null : patch.value();
    }

    private static Map<String, List<String>> copyActions(Map<String, List<String>> source) {
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null && !value.isEmpty()) {
                copy.put(key.toLowerCase(Locale.ROOT), List.copyOf(value));
            }
        });
        return Map.copyOf(copy);
    }
}
