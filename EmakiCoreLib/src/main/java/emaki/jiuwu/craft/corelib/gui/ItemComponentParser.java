package emaki.jiuwu.craft.corelib.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemParser;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.item.LegacyConfiguredItemConverter;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

@Deprecated(forRemoval = false)
public final class ItemComponentParser {

    private static final ConcurrentHashMap<String, Enchantment> ENCHANTMENT_CACHE = new ConcurrentHashMap<>();

    public record ItemComponents(String displayName,
            boolean loreConfigured,
            List<String> lore,
            String itemModel,
            Integer customModelData,
            Map<String, Integer> enchantments,
            List<String> hiddenComponents,
            Object displayNameConfig,
            Object loreConfig) {

        public ItemComponents       {
            lore = lore == null ? List.of() : List.copyOf(lore);
            enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
            hiddenComponents = hiddenComponents == null ? List.of() : List.copyOf(hiddenComponents);
            displayNameConfig = ConfigNodes.toPlainData(displayNameConfig);
            loreConfig = ConfigNodes.toPlainData(loreConfig);
        }

        public ItemComponents(String displayName,
                boolean loreConfigured,
                List<String> lore,
                String itemModel,
                Integer customModelData,
                Map<String, Integer> enchantments,
                List<String> hiddenComponents) {
            this(displayName, loreConfigured, lore, itemModel, customModelData, enchantments, hiddenComponents,
                    null, null);
        }
    }

    private ItemComponentParser() {
    }

    public static ItemComponents empty() {
        return new ItemComponents(null, false, List.of(), null, null, Map.of(), List.of());
    }

    public static ConfiguredItemDefinition toDefinition(String item,
            ItemComponents components,
            int amount,
            Map<String, ?> replacements) {
        ItemComponents value = components == null ? empty() : components;
        Map<String, Object> legacy = new LinkedHashMap<>();
        if (value.displayNameConfig() != null) {
            legacy.put("display_name", value.displayNameConfig());
        } else if (value.displayName() != null) {
            legacy.put("display_name", value.displayName());
        }
        if (value.loreConfigured()) {
            legacy.put("lore", value.loreConfig() == null ? value.lore() : value.loreConfig());
        }
        if (value.itemModel() != null) {
            legacy.put("item_model", value.itemModel());
        }
        if (value.customModelData() != null) {
            legacy.put("custom_model_data", value.customModelData());
        }
        if (!value.enchantments().isEmpty()) {
            legacy.put("enchantments", value.enchantments());
        }
        if (!value.hiddenComponents().isEmpty()) {
            legacy.put("hidden_components", value.hiddenComponents());
        }
        return new LegacyConfiguredItemConverter(new ConfiguredItemParser())
                .convert(item, amount, legacy, replacements);
    }

    public static ItemComponents fromDefinition(ConfiguredItemDefinition definition) {
        if (definition == null || definition.components().isEmpty()) {
            return empty();
        }
        Object customName = setValue(definition, "minecraft:custom_name");
        Object loreValue = setValue(definition, "minecraft:lore");
        Object itemModelValue = setValue(definition, "minecraft:item_model");
        Object customModelDataValue = setValue(definition, "minecraft:custom_model_data");
        Object enchantmentsValue = setValue(definition, "minecraft:enchantments");
        Object tooltipValue = setValue(definition, "minecraft:tooltip_display");

        List<String> lore = loreValue instanceof List<?> list
                ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : loreValue instanceof String text ? List.of(text) : List.of();
        Map<String, Integer> enchantments = legacyEnchantments(enchantmentsValue);
        List<String> hiddenComponents = legacyHiddenComponents(tooltipValue);
        return new ItemComponents(
                customName instanceof String text ? text : null,
                loreValue != null,
                lore,
                itemModelValue instanceof String text ? text : null,
                legacyCustomModelData(customModelDataValue),
                enchantments,
                hiddenComponents,
                customName instanceof Map<?, ?> ? customName : null,
                null
        );
    }

    private static Object setValue(ConfiguredItemDefinition definition, String componentId) {
        ItemComponentPatch patch = definition.components().get(componentId);
        return patch != null && patch.operation() == ItemComponentPatch.Operation.SET ? patch.value() : null;
    }

    private static Integer legacyCustomModelData(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Numbers.tryParseInt(value, null);
        }
        Object floats = map.get("floats");
        if (floats instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static Map<String, Integer> legacyEnchantments(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Object levels = map.containsKey("levels") ? map.get("levels") : map;
        if (!(levels instanceof Map<?, ?> levelMap)) {
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        levelMap.forEach((key, level) -> {
            Integer parsed = Numbers.tryParseInt(level, null);
            if (key != null && parsed != null) {
                result.put(String.valueOf(key), parsed);
            }
        });
        return result;
    }

    private static List<String> legacyHiddenComponents(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        Object hidden = map.get("hidden_components");
        for (String entry : Texts.asStringList(hidden)) {
            result.add(entry.startsWith("minecraft:") ? entry.substring("minecraft:".length()) : entry);
        }
        if (Boolean.TRUE.equals(map.get("hide_tooltip"))) {
            result.add("tooltip");
        }
        return result;
    }

    public static boolean hasConfiguredFields(Object raw) {
        return hasDirectConfiguredFields(raw) || hasDirectConfiguredFields(ConfigNodes.get(raw, "components"));
    }

    private static boolean hasDirectConfiguredFields(Object raw) {
        return ConfigNodes.contains(raw, "display_name")
                || ConfigNodes.contains(raw, "lore")
                || ConfigNodes.contains(raw, "item_model")
                || ConfigNodes.contains(raw, "item-model")
                || ConfigNodes.contains(raw, "custom_model_data")
                || ConfigNodes.contains(raw, "custommodeldata")
                || ConfigNodes.contains(raw, "enchantments")
                || ConfigNodes.contains(raw, "hidden_components")
                || ConfigNodes.contains(raw, "hide_tooltip")
                || ConfigNodes.contains(raw, "hide-tooltip")
                || ConfigNodes.contains(raw, "tooltip_display");
    }

    public static ItemComponents parse(Object raw) {
        if (raw == null) {
            return empty();
        }
        boolean loreConfigured = legacyContains(raw, "lore");
        Object loreRaw = legacyValue(raw, "lore");
        Object displayNameRaw = legacyValue(raw, "display_name");
        boolean displayNameIsTextConfig = displayNameRaw instanceof Map<?, ?>
                || displayNameRaw instanceof emaki.jiuwu.craft.corelib.yaml.YamlSection;
        return new ItemComponents(
                displayNameIsTextConfig
                        ? null
                        : displayNameRaw == null ? null : String.valueOf(displayNameRaw),
                loreConfigured,
                parseLore(loreRaw, loreConfigured),
                legacyString(raw, "item_model", legacyString(raw, "item-model", null)),
                parseCustomModelData(
                        legacyContains(raw, "custom_model_data")
                        ? legacyValue(raw, "custom_model_data")
                        : legacyValue(raw, "custommodeldata")
                ),
                parseEnchantments(legacyValue(raw, "enchantments")),
                parseHiddenComponents(raw),
                displayNameIsTextConfig ? displayNameRaw : null,
                loreRaw instanceof Map<?, ?> || loreRaw instanceof emaki.jiuwu.craft.corelib.yaml.YamlSection
                        ? loreRaw
                        : null
        );
    }

    public static void apply(ItemMeta itemMeta, ItemComponents components) {
        if (itemMeta == null || components == null) {
            return;
        }
        if (Texts.isNotBlank(components.displayName())) {
            ItemTextBridge.customName(itemMeta, MiniMessages.parse(components.displayName()));
        }
        if (components.loreConfigured()) {
            if (components.lore().isEmpty()) {
                ItemTextBridge.lore(itemMeta, null);
            } else {
                ItemTextBridge.lore(itemMeta, components.lore().stream().map(MiniMessages::parse).toList());
            }
        }
        if (Texts.isNotBlank(components.itemModel())) {
            NamespacedKey key = NamespacedKey.fromString(components.itemModel());
            if (key != null) {
                itemMeta.setItemModel(key);
            }
        } else if (components.customModelData() != null) {
            CustomModelDataComponent component = itemMeta.getCustomModelDataComponent();
            component.setFloats(List.of(components.customModelData().floatValue()));
            itemMeta.setCustomModelDataComponent(component);
        }
        applyEnchantments(itemMeta, components.enchantments());
        applyHiddenComponents(itemMeta, components.hiddenComponents());
    }

    private static Object legacyValue(Object raw, String key) {
        if (ConfigNodes.contains(raw, key)) {
            return ConfigNodes.get(raw, key);
        }
        return ConfigNodes.get(ConfigNodes.get(raw, "components"), key);
    }

    private static boolean legacyContains(Object raw, String key) {
        return ConfigNodes.contains(raw, key)
                || ConfigNodes.contains(ConfigNodes.get(raw, "components"), key);
    }

    private static String legacyString(Object raw, String key, String fallback) {
        Object value = legacyValue(raw, key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean legacyBoolean(Object raw, String key, boolean fallback) {
        Object value = legacyValue(raw, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static List<String> parseLore(Object loreRaw, boolean loreConfigured) {
        if (!loreConfigured) {
            return List.of();
        }
        if (loreRaw == null) {
            return List.of();
        }
        if (loreRaw instanceof String text && Texts.isBlank(text)) {
            return List.of();
        }
        if (loreRaw instanceof Map<?, ?> || loreRaw instanceof emaki.jiuwu.craft.corelib.yaml.YamlSection) {
            return ExpressionEngine.evaluateStringLinesConfig(loreRaw);
        }
        return Texts.asStringList(loreRaw);
    }

    private static Integer parseCustomModelData(Object raw) {
        if (raw == null || Texts.isBlank(raw)) {
            return null;
        }
        return Numbers.tryParseInt(raw, null);
    }

    private static Map<String, Integer> parseEnchantments(Object raw) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (raw == null) {
            return result;
        }
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? null : Texts.toStringSafe(entry.getKey());
                Integer level = Numbers.tryParseInt(entry.getValue(), null);
                if (Texts.isBlank(key) || level == null || level <= 0) {
                    continue;
                }
                result.put(key, level);
            }
            return result;
        }
        for (String entry : Texts.asStringList(raw)) {
            if (Texts.isBlank(entry)) {
                continue;
            }
            String key = entry.trim();
            int level = 1;
            int separator = key.lastIndexOf(':');
            if (separator > 0) {
                Integer parsedLevel = Numbers.tryParseInt(key.substring(separator + 1), null);
                if (parsedLevel != null) {
                    level = parsedLevel;
                    key = key.substring(0, separator);
                }
            }
            if (Texts.isBlank(key) || level <= 0) {
                continue;
            }
            result.put(key, level);
        }
        return result;
    }

    private static List<String> normalizeTextList(Object raw) {
        List<String> result = new ArrayList<>();
        for (String entry : Texts.asStringList(raw)) {
            if (Texts.isNotBlank(entry)) {
                result.add(Texts.lower(entry).trim());
            }
        }
        return result;
    }

    private static List<String> parseHiddenComponents(Object raw) {
        List<String> result = new ArrayList<>(normalizeTextList(legacyValue(raw, "hidden_components")));
        if (legacyBoolean(raw, "hide_tooltip", false) || legacyBoolean(raw, "hide-tooltip", false)) {
            result.add("tooltip");
        }
        Object tooltipDisplay = legacyValue(raw, "tooltip_display");
        if (tooltipDisplay instanceof Boolean enabled && enabled) {
            result.add("tooltip");
        } else if (ConfigNodes.bool(tooltipDisplay, "hide_tooltip", false)
                || ConfigNodes.bool(tooltipDisplay, "hide-tooltip", false)) {
            result.add("tooltip");
        }
        return result;
    }

    private static void applyEnchantments(ItemMeta itemMeta, Map<String, Integer> enchantments) {
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            Enchantment enchantment = resolveEnchantment(entry.getKey());
            if (enchantment != null) {
                itemMeta.addEnchant(enchantment, entry.getValue(), true);
            }
        }
    }

    private static Enchantment resolveEnchantment(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        Enchantment cached = ENCHANTMENT_CACHE.get(trimmed);
        if (cached != null) {
            return cached;
        }
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        candidates.add(trimmed);
        candidates.add(lowered);
        candidates.add(lowered.replace('.', '_'));
        if (!lowered.contains(":")) {
            candidates.add("minecraft:" + lowered);
            candidates.add("minecraft:" + lowered.replace('.', '_'));
        }
        for (String candidate : candidates) {
            NamespacedKey key = NamespacedKey.fromString(candidate);
            if (key == null) {
                continue;
            }
            Enchantment enchantment = Registry.ENCHANTMENT.get(key);
            if (enchantment != null) {
                ENCHANTMENT_CACHE.put(trimmed, enchantment);
                return enchantment;
            }
        }
        return null;
    }

    private static void applyHiddenComponents(ItemMeta itemMeta, List<String> hiddenComponents) {
        boolean hideTooltip = false;
        for (String component : hiddenComponents) {
            ItemFlag flag = mapHiddenFlag(component);
            if (flag != null) {
                itemMeta.addItemFlags(flag);
            }
            if ("tooltip".equals(component) || "tooltip_display".equals(component) || "*".equals(component)) {
                hideTooltip = true;
            }
        }
        if (hideTooltip) {
            invokeHideTooltip(itemMeta);
        }
    }

    private static ItemFlag mapHiddenFlag(String component) {
        return switch (Texts.lower(component)) {
            case "enchantments", "enchants", "enchant" ->
                ItemFlag.HIDE_ENCHANTS;
            case "attributes", "attribute_modifiers", "attribute_modifier" ->
                ItemFlag.HIDE_ATTRIBUTES;
            case "unbreakable" ->
                ItemFlag.HIDE_UNBREAKABLE;
            case "can_destroy" ->
                ItemFlag.HIDE_DESTROYS;
            case "can_place_on" ->
                ItemFlag.HIDE_PLACED_ON;
            case "trim", "armor_trim" ->
                ItemFlag.HIDE_ARMOR_TRIM;
            case "dye", "dyed_color" ->
                ItemFlag.HIDE_DYE;
            default ->
                null;
        };
    }

    private static void invokeHideTooltip(ItemMeta itemMeta) {
        itemMeta.setHideTooltip(true);
    }
}
