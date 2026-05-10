package emaki.jiuwu.craft.item.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Material;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class EmakiItemDefinitionParser {

    private final Logger logger;

    public EmakiItemDefinitionParser(Logger logger) {
        this.logger = logger;
    }

    public EmakiItemDefinition parse(YamlSection root, String source) {
        if (root == null || root.isEmpty()) {
            return null;
        }
        String id = Texts.normalizeId(root.getString("id"));
        String materialName = root.getString("material", "");
        Material material = ItemSourceUtil.resolveVanillaMaterial(materialName);
        if (Texts.isBlank(id) || material == null || !material.isItem()) {
            warning("Skipping item definition " + source + ": invalid id or material '" + materialName + "'.");
            return null;
        }
        Map<String, Object> variables = new LinkedHashMap<>(toPlainMap(root.get("ea_attributes")));
        variables.putAll(toPlainMap(root.get("variables")));
        ItemComponentsConfig components = parseComponents(root.getSection("components"), id);
        boolean random = containsRandom(root.get("lore"))
                || containsRandom(root.get("display_name"))
                || containsRandom(root.get("variables"))
                || components.attributeModifiers().stream().anyMatch(VanillaAttributeModifierConfig::randomAmount);
        return new EmakiItemDefinition(
                id,
                material,
                root.get("display_name"),
                root.getString("item_name", ""),
                root.get("lore"),
                variables,
                components,
                toDoubleMap(root.get("ea_attributes")),
                toStringMap(root.get("ea_attribute_meta")),
                normalizedList(root.get("es_skills")),
                parseSetMembership(root.getSection("set")),
                parseConditions(root.getSection("conditions")),
                parseActions(root.getSection("actions")),
                parseUpdate(root.getSection("update"), id, source),
                parseRepair(root.getSection("repair")),
                random
        );
    }

    private ItemComponentsConfig parseComponents(YamlSection section, String itemId) {
        if (section == null) {
            return ItemComponentsConfig.empty();
        }
        List<VanillaAttributeModifierConfig> modifiers = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("attribute_modifiers")) {
            Object amount = ConfigNodes.toPlainData(entry.get("amount"));
            String attribute = Texts.normalizeId(Texts.toStringSafe(entry.get("attribute")));
            if (Texts.isBlank(attribute) || amount == null) {
                continue;
            }
            modifiers.add(new VanillaAttributeModifierConfig(
                    attribute,
                    amount,
                    Texts.toStringSafe(entry.containsKey("operation") ? entry.get("operation") : "add_number"),
                    Texts.toStringSafe(entry.containsKey("slot") ? entry.get("slot") : "any"),
                    Texts.toStringSafe(entry.containsKey("name") ? entry.get("name") : "emakiitem:" + itemId + "/" + attribute),
                    containsRandom(amount)
            ));
        }
        return new ItemComponentsConfig(
                section.get("custom_model_data"),
                section.getString("item_model", ""),
                section.getString("tooltip_style", ""),
                toIntegerMap(section.get("enchantments")),
                normalizedList(section.get("item_flags")),
                section.getBoolean("hide_tooltip", false),
                section.getBoolean("unbreakable", false),
                section.getBoolean("enchantment_glint_override", null),
                section.getInt("max_stack_size", null),
                section.getString("rarity", ""),
                section.getInt("damage", null),
                section.getInt("max_damage", null),
                section.getInt("enchantable", null),
                modifiers,
                section.getString("raw", "")
        );
    }

    private ItemConditions parseConditions(YamlSection section) {
        if (section == null) {
            return ItemConditions.empty();
        }
        return new ItemConditions(
                normalizedList(section.get("entries")),
                section.getString("type", "all_of"),
                section.getInt("required_count", 1),
                section.getBoolean("invalid_as_failure", true),
                section.getString("deny_message", ""),
                normalizedList(section.get("deny_actions"))
        );
    }

    private Map<String, List<String>> parseActions(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, List<String>> actions = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            List<String> lines = normalizedList(section.get(key));
            if (!lines.isEmpty()) {
                actions.put(Texts.normalizeId(key), lines);
            }
        }
        return actions;
    }

    private ItemUpdatePolicy parseUpdate(YamlSection section, String itemId, String source) {
        if (section == null) {
            return ItemUpdatePolicy.defaults();
        }
        boolean enabled = Boolean.TRUE.equals(section.getBoolean("enabled", null));
        Integer configuredVersion = section.getInt("version", null);
        if (!enabled) {
            return ItemUpdatePolicy.defaults();
        }
        if (configuredVersion == null || configuredVersion < 1) {
            warning("Item definition " + source + " enables update for '" + itemId + "' but has no valid update.version; item updates are disabled.");
            return ItemUpdatePolicy.defaults();
        }
        return new ItemUpdatePolicy(
                configuredVersion,
                true,
                section.getBoolean("preserve_amount", null),
                section.getBoolean("preserve_damage", null),
                section.getBoolean("preserve_unknown_attribute_sources", null),
                parseUpdateTriggers(section.getSection("triggers"))
        );
    }

    private ItemUpdatePolicy.TriggerPolicy parseUpdateTriggers(YamlSection section) {
        if (section == null) {
            return ItemUpdatePolicy.TriggerPolicy.empty();
        }
        return new ItemUpdatePolicy.TriggerPolicy(
                section.getBoolean("join", null),
                section.getBoolean("held_change", null),
                section.getBoolean("inventory_click", null),
                section.getBoolean("inventory_drag", null),
                section.getBoolean("pickup", null),
                section.getBoolean("interact", null),
                section.getBoolean("command", null)
        );
    }

    private ItemSetMembership parseSetMembership(YamlSection section) {
        if (section == null) {
            return ItemSetMembership.empty();
        }
        return new ItemSetMembership(section.getString("id", ""), section.getString("piece", ""));
    }

    private RepairConfig parseRepair(YamlSection section) {
        if (section == null) {
            return RepairConfig.disabled();
        }
        boolean enabled = Boolean.TRUE.equals(section.getBoolean("enabled", false));
        if (!enabled) {
            return RepairConfig.disabled();
        }
        List<RepairMaterial> materials = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("materials")) {
            if (entry == null) {
                continue;
            }
            String item = Texts.toStringSafe(ConfigNodes.get(entry, "item"));
            int amount = Numbers.tryParseInt(ConfigNodes.get(entry, "amount"), 1);
            String restore = Texts.toStringSafe(ConfigNodes.get(entry, "restore"));
            if (Texts.isNotBlank(item) && Texts.isNotBlank(restore)) {
                materials.add(new RepairMaterial(item, amount, restore));
            }
        }
        DisabledDisplay disabledDisplay = parseDisabledDisplay(section.getSection("disabled_display"));
        List<String> onDisabled = normalizedList(section.get("on_disabled"));
        List<String> onRepaired = normalizedList(section.get("on_repaired"));
        return new RepairConfig(true, materials, disabledDisplay, onDisabled, onRepaired);
    }

    private DisabledDisplay parseDisabledDisplay(YamlSection section) {
        if (section == null) {
            return DisabledDisplay.empty();
        }
        return new DisabledDisplay(
                section.getString("name_prefix", ""),
                normalizedList(section.get("lore_append"))
        );
    }

    private Map<String, Object> toPlainMap(Object raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            if (Texts.isNotBlank(entry.getKey())) {
                result.put(Texts.normalizeId(entry.getKey()), ConfigNodes.toPlainData(entry.getValue()));
            }
        }
        return result;
    }

    private Map<String, Double> toDoubleMap(Object raw) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            Double value = Numbers.tryParseDouble(entry.getValue(), null);
            if (Texts.isNotBlank(entry.getKey()) && value != null) {
                result.put(Texts.normalizeId(entry.getKey()), value);
            }
        }
        return result;
    }

    private Map<String, String> toStringMap(Object raw) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            if (Texts.isNotBlank(entry.getKey()) && entry.getValue() != null) {
                result.put(Texts.normalizeId(entry.getKey()), Texts.toStringSafe(entry.getValue()));
            }
        }
        return result;
    }

    private Map<String, Integer> toIntegerMap(Object raw) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            Integer value = Numbers.tryParseInt(entry.getValue(), null);
            if (Texts.isNotBlank(entry.getKey()) && value != null && value > 0) {
                result.put(Texts.toStringSafe(entry.getKey()).toLowerCase(Locale.ROOT), value);
            }
        }
        return result;
    }

    private List<String> normalizedList(Object raw) {
        List<String> result = new ArrayList<>();
        for (String entry : Texts.asStringList(raw)) {
            if (Texts.isNotBlank(entry)) {
                result.add(entry.trim());
            }
        }
        return result;
    }

    private boolean containsRandom(Object raw) {
        Object value = ConfigNodes.toPlainData(raw);
        if (value instanceof Map<?, ?> map) {
            Object type = map.get("type");
            if (type != null) {
                String normalized = Texts.normalizeId(Texts.toStringSafe(type)).replace('-', '_');
                if (List.of("random_text", "random_text_lines", "random_lines", "random_line",
                        "range", "uniform", "gaussian", "normal", "skew_normal", "triangle").contains(normalized)) {
                    return true;
                }
            }
            for (Object nested : map.values()) {
                if (containsRandom(nested)) {
                    return true;
                }
            }
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object nested : iterable) {
                if (containsRandom(nested)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void warning(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }
}
