package emaki.jiuwu.craft.item.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Material;

import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.item.ItemSource;
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
        List<Map<?, ?>> effects = root.getMapList("effects");
        Map<String, Object> variables = parseVariables(root, effects);
        Map<String, Object> attributes = parseAttributes(root, effects);
        ItemComponentsConfig components = parseComponents(root.getSection("components"), id);
        boolean random = containsRandom(root.get("lore"))
                || containsRandom(root.get("display_name"))
                || containsRandom(root.get("name_actions"))
                || containsRandom(root.get("lore_actions"))
                || containsRandom(root.get("variables"))
                || containsRandom(effects)
                || components.attributeModifiers().stream().anyMatch(VanillaAttributeModifierConfig::randomAmount);
        return new EmakiItemDefinition(
                id,
                material,
                root.get("display_name"),
                root.getString("item_name", ""),
                root.get("lore"),
                parseDisplayActions(root, effects, "name_action", "name_actions", "name_action"),
                parseDisplayActions(root, effects, "lore_action", "lore_actions", "lore_action"),
                variables,
                components,
                attributes,
                parseSkills(root, effects),
                parseEquipSlot(root, id, source),
                parseSetMembership(root.getSection("set")),
                parseConditions(root),
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

    private ItemConditions parseConditions(YamlSection root) {
        return root == null ? ItemConditions.empty() : new ItemConditions(ConditionBlock.fromRoot(root, true, false));
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

    private String parseEquipSlot(YamlSection root, String itemId, String source) {
        String configured = root.getString("equip_slot", EquipmentSlotMatcher.SLOT_ALL);
        String normalized = EquipmentSlotMatcher.normalizeRequired(configured);
        if (isSupportedEquipSlot(normalized)) {
            return normalized;
        }
        warning("Item definition " + source + " configures unsupported equip_slot '" + configured
                + "' for '" + itemId + "'; falling back to 'all'.");
        return EquipmentSlotMatcher.SLOT_ALL;
    }

    private boolean isSupportedEquipSlot(String slot) {
        return switch (slot) {
            case EquipmentSlotMatcher.SLOT_ALL,
                EquipmentSlotMatcher.SLOT_HAND,
                EquipmentSlotMatcher.SLOT_MAIN_HAND,
                EquipmentSlotMatcher.SLOT_OFF_HAND,
                EquipmentSlotMatcher.SLOT_HELMET,
                EquipmentSlotMatcher.SLOT_CHESTPLATE,
                EquipmentSlotMatcher.SLOT_LEGGINGS,
                EquipmentSlotMatcher.SLOT_BOOTS -> true;
            default -> false;
        };
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
            List<ItemSource> itemSources = parseRepairItemSources(entry);
            int amount = Numbers.tryParseInt(ConfigNodes.get(entry, "amount"), 1);
            String restore = Texts.toStringSafe(ConfigNodes.get(entry, "restore"));
            if (!itemSources.isEmpty() && Texts.isNotBlank(restore)) {
                materials.add(new RepairMaterial(itemSources, amount, restore));
            }
        }
        RepairEconomyConfig economy = parseRepairEconomy(section.getSection("economy"));
        DisabledDisplay disabledDisplay = parseDisabledDisplay(section.getSection("disabled_display"));
        List<String> onDisabled = normalizedList(section.get("on_disabled"));
        List<String> onRepaired = normalizedList(section.get("on_repaired"));
        return new RepairConfig(true, materials, economy, disabledDisplay, onDisabled, onRepaired);
    }

    private List<ItemSource> parseRepairItemSources(Map<?, ?> entry) {
        Object rawSources = ConfigNodes.get(entry, "item_sources");
        if (rawSources == null) {
            rawSources = ConfigNodes.get(entry, "item_source");
        }
        if (rawSources == null) {
            rawSources = ConfigNodes.get(entry, "item");
        }
        List<ItemSource> result = new ArrayList<>();
        for (Object rawSource : ConfigNodes.asObjectList(rawSources)) {
            ItemSource source = ItemSourceUtil.parse(rawSource);
            if (source != null) {
                result.add(source);
            }
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private RepairEconomyConfig parseRepairEconomy(YamlSection section) {
        if (section == null) {
            return RepairEconomyConfig.disabled();
        }
        List<RepairCurrencyCost> currencies = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("currencies")) {
            if (entry == null) {
                continue;
            }
            String currencyId = ConfigNodes.string(entry, "currency_id", ConfigNodes.string(entry, "currency", ""));
            String costFormula = ConfigNodes.string(entry, "cost_formula", ConfigNodes.string(entry, "formula", ""));
            RepairCurrencyCost currency = new RepairCurrencyCost(
                    ConfigNodes.string(entry, "provider", "auto"),
                    currencyId,
                    Numbers.tryParseDouble(ConfigNodes.get(entry, "amount"), 0D),
                    Numbers.tryParseDouble(ConfigNodes.get(entry, "base_cost"), 0D),
                    costFormula,
                    ConfigNodes.string(entry, "display_name", "")
            );
            if (currency.hasCost()) {
                currencies.add(currency);
            }
        }
        Boolean enabledValue = section.getBoolean("enabled");
        boolean enabled = enabledValue != null ? enabledValue : !currencies.isEmpty();
        String restore = section.getString("restore", "100%");
        return new RepairEconomyConfig(enabled, restore, currencies);
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

    private Map<String, Object> parseVariables(YamlSection root, List<Map<?, ?>> effects) {
        Map<String, Object> result = new LinkedHashMap<>();
        mergePlainMap(result, root.get("variables"));
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !"variables".equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            mergePlainMap(result, ConfigNodes.get(effect, "variables"));
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private Map<String, Object> parseAttributes(YamlSection root, List<Map<?, ?>> effects) {
        Map<String, Object> result = new LinkedHashMap<>();
        mergePlainMap(result, root.get("ea_attributes"));
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !"ea_attribute".equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            mergePlainMap(result, ConfigNodes.get(effect, "ea_attributes"));
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private List<String> parseSkills(YamlSection root, List<Map<?, ?>> effects) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>(normalizedList(root.get("es_skills")));
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !"es_skill".equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            result.addAll(normalizedList(ConfigNodes.get(effect, "es_skills")));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private Object parseDisplayActions(YamlSection root, List<Map<?, ?>> effects, String effectType, String topKey, String effectKey) {
        List<Object> actions = new ArrayList<>();
        appendDisplayActions(actions, root == null ? null : root.get(topKey));
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !effectType.equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            appendDisplayActions(actions, ConfigNodes.get(effect, topKey));
            appendDisplayActions(actions, ConfigNodes.get(effect, effectKey));
        }
        return actions.isEmpty() ? List.of() : List.copyOf(actions);
    }

    private void appendDisplayActions(List<Object> actions, Object raw) {
        if (actions == null || raw == null) {
            return;
        }
        Object plain = ConfigNodes.toPlainData(raw);
        if (plain instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (entry != null) {
                    actions.add(entry);
                }
            }
            return;
        }
        actions.add(plain);
    }

    private void mergePlainMap(Map<String, Object> target, Object raw) {
        if (target == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            if (Texts.isNotBlank(entry.getKey())) {
                target.put(Texts.normalizeId(entry.getKey()), ConfigNodes.toPlainData(entry.getValue()));
            }
        }
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
