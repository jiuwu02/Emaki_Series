package emaki.jiuwu.craft.item.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;







public record ItemComponentsConfig(Object customModelData,
        String itemModel,
        String tooltipStyle,
        Map<String, Integer> enchantments,
        List<String> itemFlags,
        boolean hideTooltip,
        boolean unbreakable,
        Boolean enchantmentGlintOverride,
        Integer maxStackSize,
        String rarity,
        Integer damage,
        Integer maxDamage,
        Integer enchantable,
        List<VanillaAttributeModifierConfig> attributeModifiers,
        String raw) {

    public ItemComponentsConfig {
        customModelData = ConfigNodes.toPlainData(customModelData);
        itemModel = itemModel == null ? "" : itemModel;
        tooltipStyle = tooltipStyle == null ? "" : tooltipStyle;
        enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
        itemFlags = itemFlags == null ? List.of() : List.copyOf(itemFlags);
        rarity = rarity == null ? "" : rarity;
        attributeModifiers = attributeModifiers == null ? List.of() : List.copyOf(attributeModifiers);
        raw = raw == null ? "" : raw;
    }

    public static ItemComponentsConfig empty() {
        return new ItemComponentsConfig(null, "", "", Map.of(), List.of(), false, false, null, null, "", null, null, null, List.of(), "");
    }

    public static ItemComponentsConfig fromDefinition(ConfiguredItemDefinition definition) {
        Map<String, ItemComponentPatch> patches = definition == null ? Map.of() : definition.components();
        Map<String, Object> tooltip = mapValue(patches, "minecraft:tooltip_display");
        Object enchantmentValue = value(patches, "minecraft:enchantments");
        Map<String, Integer> enchantments = integerMap(componentValue(enchantmentValue, "levels"));
        List<String> hiddenComponents = Texts.asStringList(tooltip.get("hidden_components"));
        return new ItemComponentsConfig(
                legacyCustomModelData(value(patches, "minecraft:custom_model_data")),
                stringValue(patches, "minecraft:item_model"),
                stringValue(patches, "minecraft:tooltip_style"),
                enchantments,
                hiddenComponents.stream().map(ItemComponentsConfig::legacyFlag).toList(),
                Boolean.TRUE.equals(tooltip.get("hide_tooltip")),
                isSet(patches, "minecraft:unbreakable"),
                booleanValue(patches, "minecraft:enchantment_glint_override"),
                integerValue(patches, "minecraft:max_stack_size"),
                stringValue(patches, "minecraft:rarity"),
                integerValue(patches, "minecraft:damage"),
                integerValue(patches, "minecraft:max_damage"),
                integerValue(patches, "minecraft:enchantable", "value"),
                attributeModifiers(value(patches, "minecraft:attribute_modifiers")),
                ""
        );
    }


    public Map<String, ItemComponentPatch> toComponentPatches() {
        return toComponentPatches("");
    }

    Map<String, ItemComponentPatch> toComponentPatches(String itemId) {
        Map<String, ItemComponentPatch> patches = new LinkedHashMap<>();
        put(patches, "minecraft:custom_model_data", normalizeCustomModelData(customModelData));
        putText(patches, "minecraft:item_model", itemModel);
        putText(patches, "minecraft:tooltip_style", tooltipStyle);
        if (!enchantments.isEmpty()) {
            patches.put("minecraft:enchantments", ItemComponentPatch.set(enchantments));
        }
        Map<String, Object> tooltip = new LinkedHashMap<>();
        List<String> hiddenComponents = itemFlags.stream()
                .map(ItemComponentsConfig::componentForFlag)
                .filter(Texts::isNotBlank)
                .distinct()
                .toList();
        if (!hiddenComponents.isEmpty()) {
            tooltip.put("hidden_components", hiddenComponents);
        }
        if (hideTooltip) {
            tooltip.put("hide_tooltip", true);
        }
        if (!tooltip.isEmpty()) {
            patches.put("minecraft:tooltip_display", ItemComponentPatch.set(tooltip));
        }
        if (unbreakable) {
            patches.put("minecraft:unbreakable", ItemComponentPatch.set(Map.of()));
        }
        put(patches, "minecraft:enchantment_glint_override", enchantmentGlintOverride);
        put(patches, "minecraft:max_stack_size", maxStackSize);
        putText(patches, "minecraft:rarity", rarity);
        put(patches, "minecraft:damage", damage);
        put(patches, "minecraft:max_damage", maxDamage);
        if (enchantable != null) {
            patches.put("minecraft:enchantable", ItemComponentPatch.set(Map.of("value", enchantable)));
        }
        if (!attributeModifiers.isEmpty()) {
            List<Map<String, Object>> modifiers = new ArrayList<>();
            for (VanillaAttributeModifierConfig modifier : attributeModifiers) {
                if (modifier == null) {
                    continue;
                }
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("type", componentAttributeType(modifier.attribute()));
                value.put("amount", ConfigNodes.toPlainData(modifier.amount()));
                value.put("operation", componentOperation(modifier.operation()));
                value.put("slot", componentSlot(modifier.slot()));
                String modifierId = Texts.isNotBlank(modifier.name())
                        ? modifier.name()
                        : "emakiitem:" + (Texts.isBlank(itemId) ? "legacy" : Texts.normalizeId(itemId))
                                + "/" + Texts.normalizeId(modifier.attribute());
                value.put("id", namespaced(modifierId));
                modifiers.add(value);
            }
            if (!modifiers.isEmpty()) {
                patches.put("minecraft:attribute_modifiers", ItemComponentPatch.set(modifiers));
            }
        }
        return patches.isEmpty() ? Map.of() : Map.copyOf(patches);
    }

    private static Object legacyCustomModelData(Object raw) {
        Map<String, Object> value = new LinkedHashMap<>();
        ConfigNodes.entries(raw).forEach((key, entry) -> value.put(key, ConfigNodes.toPlainData(entry)));
        if (value.isEmpty() || value.keySet().stream().anyMatch(key -> !"floats".equals(key))) {
            return raw;
        }
        List<Object> floats = ConfigNodes.asObjectList(value.get("floats"));
        if (floats.size() != 1) {
            return raw;
        }
        Object first = floats.getFirst();
        if (first instanceof Number number) {
            double numeric = number.doubleValue();
            return numeric == Math.rint(numeric) && numeric >= Integer.MIN_VALUE && numeric <= Integer.MAX_VALUE
                    ? (int) numeric
                    : numeric;
        }
        return ConfigNodes.toPlainData(first);
    }

    private static Object normalizeCustomModelData(Object raw) {
        Object value = ConfigNodes.toPlainData(raw);
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map
                && (map.containsKey("floats") || map.containsKey("flags")
                || map.containsKey("strings") || map.containsKey("colors"))) {
            return value;
        }
        Double numeric = Numbers.tryParseDouble(value, null);
        Object floatValue = numeric == null ? value : numeric.floatValue();
        return Map.of("floats", List.of(floatValue));
    }

    private static String componentOperation(String raw) {
        return switch (Texts.normalizeId(raw)) {
            case "add_scalar" -> "add_multiplied_base";
            case "multiply_scalar_1" -> "add_multiplied_total";
            case "add_number" -> "add_value";
            default -> Texts.toStringSafe(raw);
        };
    }

    private static String legacyOperation(String raw) {
        return switch (Texts.normalizeId(raw)) {
            case "add_multiplied_base" -> "add_scalar";
            case "add_multiplied_total" -> "multiply_scalar_1";
            case "add_value" -> "add_number";
            default -> Texts.toStringSafe(raw);
        };
    }

    private static String componentSlot(String raw) {
        return switch (Texts.normalizeId(raw)) {
            case "hand", "mainhand", "main_hand" -> "hand";
            case "offhand", "off_hand" -> "offhand";
            case "head", "helmet" -> "head";
            case "chest", "chestplate" -> "chest";
            case "legs", "leggings" -> "legs";
            case "feet", "boots" -> "feet";
            default -> "any";
        };
    }

    private static void put(Map<String, ItemComponentPatch> patches, String id, Object value) {
        if (value != null) {
            patches.put(id, ItemComponentPatch.set(ConfigNodes.toPlainData(value)));
        }
    }

    private static void putText(Map<String, ItemComponentPatch> patches, String id, String value) {
        if (Texts.isNotBlank(value)) {
            patches.put(id, ItemComponentPatch.set(value));
        }
    }

    private static boolean isSet(Map<String, ItemComponentPatch> patches, String id) {
        ItemComponentPatch patch = patches.get(id);
        return patch != null && patch.operation() == ItemComponentPatch.Operation.SET;
    }

    private static Object value(Map<String, ItemComponentPatch> patches, String id) {
        ItemComponentPatch patch = patches.get(id);
        return patch == null || patch.operation() != ItemComponentPatch.Operation.SET ? null : patch.value();
    }

    private static String stringValue(Map<String, ItemComponentPatch> patches, String id) {
        return Texts.toStringSafe(value(patches, id));
    }

    private static Integer integerValue(Map<String, ItemComponentPatch> patches, String id) {
        return Numbers.tryParseInt(value(patches, id), null);
    }

    private static Integer integerValue(Map<String, ItemComponentPatch> patches, String id, String nestedKey) {
        return Numbers.tryParseInt(componentValue(value(patches, id), nestedKey), null);
    }

    private static Object componentValue(Object raw, String nestedKey) {
        return ConfigNodes.contains(raw, nestedKey) ? ConfigNodes.get(raw, nestedKey) : raw;
    }

    private static Boolean booleanValue(Map<String, ItemComponentPatch> patches, String id) {
        Object value = value(patches, id);
        return value instanceof Boolean bool ? bool : value == null ? null : Boolean.parseBoolean(String.valueOf(value));
    }

    private static Map<String, Object> mapValue(Map<String, ItemComponentPatch> patches, String id) {
        Object raw = value(patches, id);
        Map<String, Object> result = new LinkedHashMap<>();
        ConfigNodes.entries(raw).forEach((key, entry) -> result.put(key, ConfigNodes.toPlainData(entry)));
        return result;
    }

    private static Map<String, Integer> integerMap(Object raw) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            Integer level = Numbers.tryParseInt(entry.getValue(), null);
            if (Texts.isNotBlank(entry.getKey()) && level != null && level > 0) {
                result.put(entry.getKey(), level);
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private static List<VanillaAttributeModifierConfig> attributeModifiers(Object raw) {
        Object modifiersRaw = raw instanceof Iterable<?> ? raw : ConfigNodes.get(raw, "modifiers");
        List<VanillaAttributeModifierConfig> result = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(modifiersRaw)) {
            String attribute = stripMinecraftNamespace(ConfigNodes.string(entry, "type", ""));
            Object amount = ConfigNodes.toPlainData(ConfigNodes.get(entry, "amount"));
            if (Texts.isBlank(attribute) || amount == null) {
                continue;
            }
            result.add(new VanillaAttributeModifierConfig(
                    attribute,
                    amount,
                    legacyOperation(ConfigNodes.string(entry, "operation", "add_value")),
                    ConfigNodes.string(entry, "slot", "any"),
                    ConfigNodes.string(entry, "id", ConfigNodes.string(entry, "name", "")),
                    amount instanceof Map<?, ?>
            ));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private static String componentAttributeType(String value) {
        String normalized = namespaced(value);
        String legacyPrefix = "minecraft:generic.";
        return normalized.startsWith(legacyPrefix)
                ? "minecraft:" + normalized.substring(legacyPrefix.length())
                : normalized;
    }

    private static String namespaced(String value) {
        String normalized = Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() || normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }

    private static String stripMinecraftNamespace(String value) {
        String normalized = Texts.toStringSafe(value);
        return normalized.startsWith("minecraft:") ? normalized.substring("minecraft:".length()) : normalized;
    }

    private static String componentForFlag(String raw) {
        return switch (Texts.toStringSafe(raw).trim().toUpperCase(Locale.ROOT)) {
            case "HIDE_ENCHANTS" -> "minecraft:enchantments";
            case "HIDE_ATTRIBUTES" -> "minecraft:attribute_modifiers";
            case "HIDE_UNBREAKABLE" -> "minecraft:unbreakable";
            case "HIDE_DESTROYS" -> "minecraft:can_break";
            case "HIDE_PLACED_ON" -> "minecraft:can_place_on";
            case "HIDE_ADDITIONAL_TOOLTIP", "HIDE_POTION_EFFECTS" -> "minecraft:potion_contents";
            case "HIDE_DYE" -> "minecraft:dyed_color";
            case "HIDE_ARMOR_TRIM" -> "minecraft:trim";
            default -> namespaced(raw);
        };
    }

    private static String legacyFlag(String raw) {
        return switch (Texts.toStringSafe(raw).toLowerCase(Locale.ROOT)) {
            case "minecraft:enchantments" -> "HIDE_ENCHANTS";
            case "minecraft:attribute_modifiers" -> "HIDE_ATTRIBUTES";
            case "minecraft:unbreakable" -> "HIDE_UNBREAKABLE";
            case "minecraft:can_break" -> "HIDE_DESTROYS";
            case "minecraft:can_place_on" -> "HIDE_PLACED_ON";
            case "minecraft:potion_contents" -> "HIDE_ADDITIONAL_TOOLTIP";
            case "minecraft:dyed_color" -> "HIDE_DYE";
            case "minecraft:trim" -> "HIDE_ARMOR_TRIM";
            default -> raw;
        };
    }
}
