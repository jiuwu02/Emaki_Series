package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.ItemComponentsConfig;
import emaki.jiuwu.craft.item.model.VanillaAttributeModifierConfig;

public final class EmakiItemFactory {

    private final EmakiItemLoader loader;
    private final EmakiItemPdcWriter pdcWriter;
    private final ConcurrentHashMap<String, ItemStack> prototypeCache = new ConcurrentHashMap<>();

    public EmakiItemFactory(EmakiItemLoader loader, EmakiItemPdcWriter pdcWriter) {
        this.loader = loader;
        this.pdcWriter = pdcWriter;
    }

    public ItemStack create(String id, int amount) {
        EmakiItemDefinition definition = loader.get(id);
        if (definition == null) {
            return null;
        }
        ItemStack itemStack = definition.hasRandomElements()
                ? build(definition)
                : prototypeCache.computeIfAbsent(definition.id(), ignored -> build(definition)).clone();
        itemStack.setAmount(Math.max(1, Math.min(amount, itemStack.getMaxStackSize())));
        return itemStack;
    }

    public void clearCache() {
        prototypeCache.clear();
    }

    private ItemStack build(EmakiItemDefinition definition) {
        ItemStack itemStack = baseItem(definition);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            applyText(itemMeta, definition);
            applyComponents(itemMeta, definition);
            itemStack.setItemMeta(itemMeta);
        }
        pdcWriter.write(itemStack, definition);
        return itemStack;
    }

    private ItemStack baseItem(EmakiItemDefinition definition) {
        if (definition == null || Texts.isBlank(definition.components().raw())) {
            return new ItemStack(definition.material(), 1);
        }
        String materialKey = definition.material().getKey().toString();
        String raw = definition.components().raw().trim();
        String itemString = raw.startsWith(materialKey) ? raw : materialKey + raw;
        try {
            ItemStack itemStack = Bukkit.getItemFactory().createItemStack(itemString);
            return itemStack == null || itemStack.getType().isAir() ? new ItemStack(definition.material(), 1) : itemStack;
        } catch (IllegalArgumentException exception) {
            return new ItemStack(definition.material(), 1);
        }
    }

    private void applyText(ItemMeta itemMeta, EmakiItemDefinition definition) {
        Map<String, Object> variables = definition.variables();
        String displayName = ExpressionEngine.evaluateStringConfig(definition.displayName(), variables);
        if (Texts.isNotBlank(displayName)) {
            ItemTextBridge.customName(itemMeta, MiniMessages.parse(displayName));
        }
        if (Texts.isNotBlank(definition.itemName())) {
            itemMeta.setItemName(MiniMessages.legacy(MiniMessages.parse(definition.itemName())));
        }
        List<String> lore = ExpressionEngine.evaluateStringLinesConfig(definition.lore(), variables);
        if (!lore.isEmpty()) {
            ItemTextBridge.setLoreLines(itemMeta, lore);
        }
    }

    private void applyComponents(ItemMeta itemMeta, EmakiItemDefinition definition) {
        ItemComponentsConfig components = definition.components();
        applyCustomModelData(itemMeta, components.customModelData());
        if (Texts.isNotBlank(components.itemModel())) {
            NamespacedKey key = NamespacedKey.fromString(components.itemModel());
            if (key != null) {
                itemMeta.setItemModel(key);
            }
        }
        if (Texts.isNotBlank(components.tooltipStyle())) {
            NamespacedKey key = NamespacedKey.fromString(components.tooltipStyle());
            if (key != null) {
                itemMeta.setTooltipStyle(key);
            }
        }
        components.enchantments().forEach((id, level) -> {
            Enchantment enchantment = resolveEnchantment(id);
            if (enchantment != null) {
                itemMeta.addEnchant(enchantment, level, true);
            }
        });
        for (String flag : components.itemFlags()) {
            try {
                itemMeta.addItemFlags(ItemFlag.valueOf(flag.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (components.hideTooltip()) {
            itemMeta.setHideTooltip(true);
        }
        itemMeta.setUnbreakable(components.unbreakable());
        if (components.enchantmentGlintOverride() != null) {
            itemMeta.setEnchantmentGlintOverride(components.enchantmentGlintOverride());
        }
        if (components.maxStackSize() != null && components.maxStackSize() > 0) {
            itemMeta.setMaxStackSize(components.maxStackSize());
        }
        if (Texts.isNotBlank(components.rarity())) {
            try {
                itemMeta.setRarity(ItemRarity.valueOf(components.rarity().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (itemMeta instanceof Damageable damageable && components.damage() != null) {
            damageable.setDamage(Math.max(0, components.damage()));
            if (components.maxDamage() != null && components.maxDamage() > 0) {
                damageable.setMaxDamage(components.maxDamage());
            }
        }
        if (components.enchantable() != null && components.enchantable() >= 0) {
            itemMeta.setEnchantable(components.enchantable());
        }
        applyAttributeModifiers(itemMeta, definition);
    }

    private void applyCustomModelData(ItemMeta itemMeta, Object raw) {
        if (raw == null) {
            return;
        }
        if (raw instanceof Number number) {
            CustomModelDataComponent component = itemMeta.getCustomModelDataComponent();
            component.setFloats(List.of(number.floatValue()));
            itemMeta.setCustomModelDataComponent(component);
            return;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            Integer value = Numbers.tryParseInt(raw, null);
            if (value != null) {
                CustomModelDataComponent component = itemMeta.getCustomModelDataComponent();
                component.setFloats(List.of(value.floatValue()));
                itemMeta.setCustomModelDataComponent(component);
            }
            return;
        }
        CustomModelDataComponent component = itemMeta.getCustomModelDataComponent();
        component.setFloats(floatList(map.get("floats")));
        component.setFlags(booleanList(map.get("flags")));
        component.setStrings(stringList(map.get("strings")));
        component.setColors(colorList(map.get("colors")));
        itemMeta.setCustomModelDataComponent(component);
    }

    private void applyAttributeModifiers(ItemMeta itemMeta, EmakiItemDefinition definition) {
        for (VanillaAttributeModifierConfig config : definition.components().attributeModifiers()) {
            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(config.attribute()));
            Double amount = resolveNumber(config.amount(), definition.variables());
            if (attribute == null || amount == null) {
                continue;
            }
            AttributeModifier.Operation operation = switch (Texts.normalizeId(config.operation())) {
                case "add_scalar" -> AttributeModifier.Operation.ADD_SCALAR;
                case "multiply_scalar_1" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
                default -> AttributeModifier.Operation.ADD_NUMBER;
            };
            NamespacedKey key = NamespacedKey.fromString(config.name());
            if (key == null) {
                key = new NamespacedKey("emakiitem", definition.id() + "/" + config.attribute());
            }
            itemMeta.addAttributeModifier(attribute, new AttributeModifier(key, amount, operation, slot(config.slot())));
        }
    }

    private Double resolveNumber(Object raw, Map<String, Object> variables) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof Map<?, ?>) {
            return ExpressionEngine.evaluateRandomConfig(raw, variables);
        }
        return Numbers.tryParseDouble(ExpressionEngine.evaluateStringConfig(raw, variables), null);
    }

    private EquipmentSlotGroup slot(String raw) {
        return switch (Texts.normalizeId(raw)) {
            case "hand", "mainhand", "main_hand" -> EquipmentSlotGroup.HAND;
            case "off_hand", "offhand" -> EquipmentSlotGroup.OFFHAND;
            case "head", "helmet" -> EquipmentSlotGroup.HEAD;
            case "chest", "chestplate" -> EquipmentSlotGroup.CHEST;
            case "legs", "leggings" -> EquipmentSlotGroup.LEGS;
            case "feet", "boots" -> EquipmentSlotGroup.FEET;
            default -> EquipmentSlotGroup.ANY;
        };
    }

    private Enchantment resolveEnchantment(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        String keyText = raw.contains(":") ? raw : "minecraft:" + raw.toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(keyText);
        return key == null ? null : Registry.ENCHANTMENT.get(key);
    }

    private List<Float> floatList(Object raw) {
        List<Float> result = new ArrayList<>();
        for (String entry : Texts.asStringList(raw)) {
            Double value = Numbers.tryParseDouble(entry, null);
            if (value != null) {
                result.add(value.floatValue());
            }
        }
        return result;
    }

    private List<Boolean> booleanList(Object raw) {
        List<Boolean> result = new ArrayList<>();
        for (String entry : Texts.asStringList(raw)) {
            result.add(Boolean.parseBoolean(entry));
        }
        return result;
    }

    private List<String> stringList(Object raw) {
        return Texts.asStringList(raw);
    }

    private List<org.bukkit.Color> colorList(Object raw) {
        List<org.bukkit.Color> result = new ArrayList<>();
        for (String entry : Texts.asStringList(raw)) {
            String value = entry.startsWith("#") ? entry.substring(1) : entry;
            try {
                int rgb = Integer.parseInt(value, 16);
                result.add(org.bukkit.Color.fromRGB(rgb));
            } catch (RuntimeException ignored) {
            }
        }
        return result;
    }
}
