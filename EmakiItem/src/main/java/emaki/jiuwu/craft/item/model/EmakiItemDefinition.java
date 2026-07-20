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
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceType;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;


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
    private final ItemSetMembership setMembership;
    private final ItemConditions conditions;
    private final Map<String, List<String>> actions;
    private final ItemUpdatePolicy updatePolicy;
    private final RepairConfig repair;
    private final int amount;
    private final boolean hasRandomElements;





    public EmakiItemDefinition(String id,
            Material material,
            Object displayName,
            String itemName,
            Object lore,
            Object nameActions,
            Object loreActions,
            Map<String, Object> variables,
            ItemComponentsConfig components,
            Map<String, Object> attributes,
            List<String> skills,
            Map<String, String> skillTriggers,
            String equipSlot,
            ItemSetMembership setMembership,
            ItemConditions conditions,
            Map<String, List<String>> actions,
            ItemUpdatePolicy updatePolicy,
            RepairConfig repair,
            int amount,
            boolean hasRandomElements) {
        this(
                id,
                composeItemDefinition(id, material, displayName, itemName, lore, components, amount),
                material,
                ConfigNodes.toPlainData(displayName),
                itemName == null ? "" : itemName,
                ConfigNodes.toPlainData(lore),
                components == null ? ItemComponentsConfig.empty() : components,
                nameActions,
                loreActions,
                variables,
                attributes,
                skills,
                skillTriggers,
                equipSlot,
                setMembership,
                conditions,
                actions,
                updatePolicy,
                repair,
                amount,
                hasRandomElements
        );
    }


    public EmakiItemDefinition(String id,
            ConfiguredItemDefinition itemDefinition,
            Object nameActions,
            Object loreActions,
            Map<String, Object> variables,
            Map<String, Object> attributes,
            List<String> skills,
            Map<String, String> skillTriggers,
            String equipSlot,
            ItemSetMembership setMembership,
            ItemConditions conditions,
            Map<String, List<String>> actions,
            ItemUpdatePolicy updatePolicy,
            RepairConfig repair,
            boolean hasRandomElements) {
        this(
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
                setMembership,
                conditions,
                actions,
                updatePolicy,
                repair,
                itemDefinition == null ? 1 : itemDefinition.amount(),
                hasRandomElements
        );
    }

    private EmakiItemDefinition(String id,
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
            ItemSetMembership setMembership,
            ItemConditions conditions,
            Map<String, List<String>> actions,
            ItemUpdatePolicy updatePolicy,
            RepairConfig repair,
            int amount,
            boolean hasRandomElements) {
        this.id = id == null ? "" : id;
        this.itemDefinition = itemDefinition == null
                ? new ConfiguredItemDefinition(null, Math.max(1, amount), Map.of())
                : itemDefinition;
        this.material = material;
        this.displayName = ConfigNodes.toPlainData(displayName);
        this.itemName = itemName == null ? "" : itemName;
        this.lore = ConfigNodes.toPlainData(lore);
        this.nameActions = ConfigNodes.toPlainData(nameActions);
        this.loreActions = ConfigNodes.toPlainData(loreActions);
        this.variables = variables == null ? Map.of() : Map.copyOf(variables);
        this.components = components == null ? ItemComponentsConfig.empty() : components;
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        this.skills = skills == null ? List.of() : List.copyOf(skills);
        this.skillTriggers = skillTriggers == null ? Map.of() : Map.copyOf(skillTriggers);
        this.equipSlot = EquipmentSlotMatcher.normalizeRequired(equipSlot);
        this.setMembership = setMembership == null ? ItemSetMembership.empty() : setMembership;
        this.conditions = conditions == null ? ItemConditions.empty() : conditions;
        this.actions = actions == null ? Map.of() : copyActions(actions);
        this.updatePolicy = updatePolicy == null ? ItemUpdatePolicy.defaults() : updatePolicy;
        this.repair = repair == null ? RepairConfig.disabled() : repair;
        this.amount = Math.max(1, amount);
        this.hasRandomElements = hasRandomElements;
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

    public int amount() {
        return amount;
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
        snapshot.put("amount", itemDefinition.amount());
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
        return amount == definition.amount
                && hasRandomElements == definition.hasRandomElements
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
                && setMembership.equals(definition.setMembership)
                && conditions.equals(definition.conditions)
                && actions.equals(definition.actions)
                && updatePolicy.equals(definition.updatePolicy)
                && repair.equals(definition.repair);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, itemDefinition, material, displayName, itemName, lore, nameActions, loreActions,
                variables, components, attributes, skills, skillTriggers, equipSlot, setMembership, conditions,
                actions, updatePolicy, repair, amount, hasRandomElements);
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
                + ", setMembership=" + setMembership
                + ", conditions=" + conditions
                + ", actions=" + actions
                + ", updatePolicy=" + updatePolicy
                + ", repair=" + repair
                + ", amount=" + amount
                + ", hasRandomElements=" + hasRandomElements + "]";
    }

    private static ConfiguredItemDefinition composeItemDefinition(String id,
            Material material,
            Object displayName,
            String itemName,
            Object lore,
            ItemComponentsConfig components,
            int amount) {
        Map<String, ItemComponentPatch> patches = new LinkedHashMap<>();
        if (components != null) {
            patches.putAll(components.toComponentPatches(id));
        }
        if (displayName != null) {
            patches.put("minecraft:custom_name", ItemComponentPatch.set(ConfigNodes.toPlainData(displayName)));
        }
        if (Texts.isNotBlank(itemName)) {
            patches.put("minecraft:item_name", ItemComponentPatch.set(itemName));
        }
        if (lore != null) {
            patches.put("minecraft:lore", ItemComponentPatch.set(ConfigNodes.toPlainData(lore)));
        }
        String source = material == null ? null : "minecraft-" + material.name().toLowerCase(Locale.ROOT);
        return new ConfiguredItemDefinition(source, Math.max(1, amount), patches);
    }

    private static Material projectMaterial(ConfiguredItemDefinition definition) {
        ItemSource source = definition == null ? null : ItemSourceUtil.parse(definition.source());
        return source == null || source.getType() != ItemSourceType.VANILLA
                ? null
                : ItemSourceUtil.resolveVanillaMaterial(source.getIdentifier());
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
