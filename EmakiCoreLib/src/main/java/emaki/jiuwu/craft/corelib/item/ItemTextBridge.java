package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.text.MiniMessages;
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
        return itemMeta.hasDisplayName()
                ? MiniMessages.read(itemMeta.getDisplayName())
                : MiniMessages.read(itemMeta.getItemName());
    }

    public static void customName(ItemMeta itemMeta, Component name) {
        if (itemMeta == null) {
            return;
        }
        itemMeta.setDisplayName(name == null ? null : MiniMessages.legacy(name));
    }

    public static List<Component> lore(ItemMeta itemMeta) {
        if (itemMeta == null || !itemMeta.hasLore()) {
            return null;
        }
        List<String> rawLore = itemMeta.getLore();
        if (rawLore == null) {
            return null;
        }
        List<Component> components = new ArrayList<>(rawLore.size());
        for (String line : rawLore) {
            components.add(MiniMessages.read(line));
        }
        return components;
    }

    public static void lore(ItemMeta itemMeta, List<Component> lore) {
        if (itemMeta == null) {
            return;
        }
        if (lore == null || lore.isEmpty()) {
            itemMeta.setLore(null);
            return;
        }
        List<String> lines = new ArrayList<>(lore.size());
        for (Component line : lore) {
            lines.add(MiniMessages.legacy(line));
        }
        itemMeta.setLore(lines);
    }

    public static Component effectiveName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return Component.empty();
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (hasCustomName(itemMeta)) {
            return customName(itemMeta);
        }
        return Component.text(humanizeMaterial(itemStack.getType()));
    }

    public static Component displayWithItemHover(ItemStack itemStack) {
        return displayWithItemHover(effectiveName(itemStack), itemStack);
    }

    public static Component displayWithItemHover(Component display, ItemStack itemStack) {
        HoverEvent<?> hoverEvent = showItemHover(itemStack);
        return hoverEvent == null ? display : display.hoverEvent(hoverEvent);
    }

    private static HoverEvent<?> showItemHover(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        var key = itemStack.getType().getKey();
        return HoverEvent.showItem(Key.key(key.getNamespace(), key.getKey()), Math.max(1, itemStack.getAmount()));
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
}
