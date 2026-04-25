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

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Parses GUI item component overrides and applies them onto an
 * {@link ItemMeta}.
 */
public final class ItemComponentParser {

    // 附魔解析缓存，避免每次 GUI 渲染都重复 Registry 查找
    private static final ConcurrentHashMap<String, Enchantment> ENCHANTMENT_CACHE = new ConcurrentHashMap<>();

    public record ItemComponents(String displayName,
            boolean loreConfigured,
            List<String> lore,
            String itemModel,
            Integer customModelData,
            Map<String, Integer> enchantments,
            List<String> hiddenComponents) {

        public ItemComponents       {
            lore = lore == null ? List.of() : List.copyOf(lore);
            enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
            hiddenComponents = hiddenComponents == null ? List.of() : List.copyOf(hiddenComponents);
        }
    }

    private ItemComponentParser() {
    }

    public static ItemComponents empty() {
        return new ItemComponents(null, false, List.of(), null, null, Map.of(), List.of());
    }

    public static boolean hasConfiguredFields(Object raw) {
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
        boolean loreConfigured = ConfigNodes.contains(raw, "lore");
        Object loreRaw = ConfigNodes.get(raw, "lore");
        return new ItemComponents(
                ConfigNodes.string(raw, "display_name", null),
                loreConfigured,
                parseLore(loreRaw, loreConfigured),
                ConfigNodes.string(raw, "item_model", ConfigNodes.string(raw, "item-model", null)),
                parseCustomModelData(
                        ConfigNodes.contains(raw, "custom_model_data")
                        ? ConfigNodes.get(raw, "custom_model_data")
                        : ConfigNodes.get(raw, "custommodeldata")
                ),
                parseEnchantments(ConfigNodes.get(raw, "enchantments")),
                parseHiddenComponents(raw)
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
            String[] split = entry.split(":", 2);
            String key = split[0];
            int level = split.length > 1 ? Numbers.tryParseInt(split[1], 1) : 1;
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
        List<String> result = new ArrayList<>(normalizeTextList(ConfigNodes.get(raw, "hidden_components")));
        if (ConfigNodes.bool(raw, "hide_tooltip", false) || ConfigNodes.bool(raw, "hide-tooltip", false)) {
            result.add("tooltip");
        }
        Object tooltipDisplay = ConfigNodes.get(raw, "tooltip_display");
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
