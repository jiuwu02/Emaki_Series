package emaki.jiuwu.craft.corelib.gui;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class GuiItemBuilder {

    @FunctionalInterface
    public interface ItemFactory {
        ItemStack create(ItemSource source, int amount);
    }

    public record Request(String item,
                          ItemComponentParser.ItemComponents components,
                          int amount,
                          Map<String, ?> replacements) {
        public Request {
            amount = Math.max(1, amount);
            replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
        }
    }

    private GuiItemBuilder() {
    }

    public static ItemStack build(Request request, ItemFactory itemFactory) {
        if (request == null) {
            return barrier(1);
        }
        ItemStack itemStack = baseItem(request.item(), request.amount(), itemFactory);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            ItemComponentParser.apply(itemMeta, formatComponents(request.components(), request.replacements()));
            itemStack.setItemMeta(itemMeta);
        }
        return itemStack;
    }

    public static ItemStack build(String item,
                                  ItemComponentParser.ItemComponents components,
                                  int amount,
                                  Map<String, ?> replacements,
                                  ItemFactory itemFactory) {
        return build(new Request(item, components, amount, replacements), itemFactory);
    }

    private static ItemStack baseItem(String item, int amount, ItemFactory itemFactory) {
        ItemSource source = ItemSourceUtil.parse(item);
        if (source == null) {
            return barrier(amount);
        }
        ItemStack itemStack = switch (source.getType()) {
            case VANILLA -> createVanillaItem(source.getIdentifier(), amount);
            case MMOITEMS, NEIGEITEMS, CRAFTENGINE -> itemFactory == null ? null : itemFactory.create(source, amount);
        };
        if (itemStack == null) {
            return barrier(amount);
        }
        ItemStack clone = itemStack.clone();
        clone.setAmount(amount);
        return clone;
    }

    private static ItemStack createVanillaItem(String identifier, int amount) {
        if (Texts.isBlank(identifier)) {
            return null;
        }
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = normalized.contains(":")
            ? NamespacedKey.fromString(normalized)
            : NamespacedKey.minecraft(normalized);
        Material material = key == null ? null : Registry.MATERIAL.get(key);
        return material == null ? null : new ItemStack(material, Math.max(1, amount));
    }

    private static ItemStack barrier(int amount) {
        return new ItemStack(Material.BARRIER, Math.max(1, amount));
    }

    private static ItemComponentParser.ItemComponents formatComponents(ItemComponentParser.ItemComponents components,
                                                                       Map<String, ?> replacements) {
        ItemComponentParser.ItemComponents base = components == null ? ItemComponentParser.empty() : components;
        List<String> lore = base.lore().stream().map(line -> Texts.formatTemplate(line, replacements)).toList();
        Map<String, Integer> enchantments = new LinkedHashMap<>(base.enchantments());
        return new ItemComponentParser.ItemComponents(
            Texts.formatTemplate(base.displayName(), replacements),
            base.loreConfigured(),
            lore,
            base.itemModel(),
            base.customModelData(),
            enchantments,
            base.hiddenComponents()
        );
    }
}
