package emaki.jiuwu.craft.corelib.api.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;

public final class ItemTextBridge {

    private ItemTextBridge() {
    }

    public static boolean hasCustomName(ItemMeta itemMeta) {
        return itemMeta != null && (itemMeta.hasDisplayName() || itemMeta.hasItemName());
    }

    public static Component customName(ItemMeta itemMeta) {
        if (!hasCustomName(itemMeta)) {
            return null;
        }
        Component displayName = itemMeta.displayName();
        if (displayName != null) {
            return displayName;
        }
        return itemMeta.itemName();
    }

    public static void customName(ItemMeta itemMeta, Component name) {
        if (itemMeta == null) {
            return;
        }
        itemMeta.displayName(name);
        if (name == null) {
            itemMeta.itemName(null);
        }
    }

    public static void customNameText(ItemMeta itemMeta, String name) {
        if (itemMeta == null) {
            return;
        }
        customName(itemMeta, Texts.isBlank(name) ? null : MiniMessages.parse(name));
    }

    public static List<Component> lore(ItemMeta itemMeta) {
        if (itemMeta == null || !itemMeta.hasLore()) {
            return null;
        }
        List<Component> lore = itemMeta.lore();
        if (lore == null || lore.isEmpty()) {
            return null;
        }
        return new ArrayList<>(lore);
    }

    public static void lore(ItemMeta itemMeta, List<Component> lore) {
        if (itemMeta == null) {
            return;
        }
        itemMeta.lore(lore == null || lore.isEmpty() ? null : new ArrayList<>(lore));
    }

    public static List<String> loreLines(ItemMeta itemMeta) {
        List<Component> lore = lore(itemMeta);
        if (lore == null || lore.isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>(lore.size());
        for (Component line : lore) {
            lines.add(MiniMessages.serialize(line));
        }
        return lines;
    }

    public static void setLoreLines(ItemMeta itemMeta, List<String> loreLines) {
        if (itemMeta == null) {
            return;
        }
        if (loreLines == null || loreLines.isEmpty()) {
            lore(itemMeta, null);
            return;
        }
        List<Component> lore = new ArrayList<>(loreLines.size());
        for (String line : loreLines) {
            lore.add(MiniMessages.parse(line));
        }
        lore(itemMeta, lore);
    }

    public static Component effectiveName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return Component.empty();
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (hasCustomName(itemMeta)) {
            return customName(itemMeta);
        }
        String translationKey = translationKey(itemStack.getType());
        if (translationKey != null && !translationKey.isBlank()) {
            return Component.translatable(translationKey);
        }
        return Component.text(humanizeMaterial(itemStack.getType()));
    }

    public static String effectiveNameText(ItemStack itemStack) {
        return MiniMessages.serialize(effectiveName(itemStack));
    }

    public static String effectiveNamePlain(ItemStack itemStack) {
        return MiniMessages.plain(effectiveName(itemStack));
    }

    public static Component displayWithItemHover(ItemStack itemStack) {
        return displayWithItemHover(effectiveName(itemStack), itemStack);
    }

    public static Component displayWithItemHover(Component display, ItemStack itemStack) {
        HoverEvent<?> hoverEvent = showItemHover(itemStack);
        return hoverEvent == null ? display : display.hoverEvent(hoverEvent);
    }

    public static String displayWithItemHoverText(ItemStack itemStack) {
        return MiniMessages.serialize(displayWithItemHover(itemStack));
    }

    public static String displayWithItemHoverText(String displayText, ItemStack itemStack) {
        if (Texts.isBlank(displayText)) {
            return displayWithItemHoverText(itemStack);
        }
        return MiniMessages.serialize(displayWithItemHover(MiniMessages.parse(displayText), itemStack));
    }

    private static HoverEvent<?> showItemHover(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        NamespacedKey key = resolveMaterialKey(itemStack.getType());
        return HoverEvent.showItem(Key.key(key.getNamespace(), key.getKey()), Math.max(1, itemStack.getAmount()));
    }

    private static NamespacedKey resolveMaterialKey(Material material) {
        if (material == null) {
            return NamespacedKey.minecraft("air");
        }
        return NamespacedKey.minecraft(material.name().toLowerCase(Locale.ROOT));
    }

    private static String humanizeMaterial(Material material) {
        if (material == null) {
            return "";
        }
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private static String translationKey(Material material) {
        if (material == null) {
            return "";
        }
        try {
            if (material.isItem()) {
                return material.getItemTranslationKey();
            }
            if (material.isBlock()) {
                return material.getBlockTranslationKey();
            }
            return material.getTranslationKey();
        } catch (RuntimeException _) {
            return "";
        }
    }

    /**
     * {@return the {@code item.minecraft.*} translation key for a material, or an
     * empty string when the material is not an item}
     *
     * <p>Exposed so a server-side translation table can be queried directly.
     * A material that only exists as a block has no item key, so callers should
     * fall back to {@link #blockTranslationKey(Material)}.
     *
     * @param material the material to describe, may be {@code null}
     */
    public static String itemTranslationKey(Material material) {
        if (material == null) {
            return "";
        }
        try {
            return material.isItem() ? material.getItemTranslationKey() : "";
        } catch (RuntimeException _) {
            return "";
        }
    }

    /**
     * {@return the {@code block.minecraft.*} translation key for a material, or an
     * empty string when the material is not a block}
     *
     * @param material the material to describe, may be {@code null}
     */
    public static String blockTranslationKey(Material material) {
        if (material == null) {
            return "";
        }
        try {
            return material.isBlock() ? material.getBlockTranslationKey() : "";
        } catch (RuntimeException _) {
            return "";
        }
    }

}
