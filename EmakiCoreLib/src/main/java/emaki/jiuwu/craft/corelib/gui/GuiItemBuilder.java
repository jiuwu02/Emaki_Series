package emaki.jiuwu.craft.corelib.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class GuiItemBuilder {

    @FunctionalInterface
    public interface ItemFactory {

        ItemStack create(ItemSource source, int amount);
    }

    public record Request(String item,
            ItemComponentParser.ItemComponents components,
            int amount,
            Map<String, ?> replacements) {

        public Request    {
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

    public static ItemStack apply(ItemStack baseItem,
            ItemComponentParser.ItemComponents components,
            Map<String, ?> replacements) {
        ItemStack itemStack = baseItem == null ? barrier(1) : baseItem.clone();
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            ItemComponentParser.apply(itemMeta, formatComponents(components, replacements));
            itemStack.setItemMeta(itemMeta);
        }
        return itemStack;
    }

    private static ItemStack baseItem(String item, int amount, ItemFactory itemFactory) {
        ItemSource source = ItemSourceUtil.parse(item);
        if (source == null) {
            return barrier(amount);
        }
        ItemStack itemStack = switch (source.getType()) {
            case VANILLA ->
                createVanillaItem(source.getIdentifier(), amount);
            case MMOITEMS, ITEMSADDER, NEIGEITEMS, NEXO, CRAFTENGINE ->
                itemFactory == null ? null : itemFactory.create(source, amount);
        };
        if (itemStack == null) {
            return barrier(amount);
        }
        ItemStack clone = itemStack.clone();
        clone.setAmount(amount);
        return clone;
    }

    private static ItemStack createVanillaItem(String identifier, int amount) {
        Material material = ItemSourceUtil.resolveVanillaMaterial(identifier);
        return material == null ? null : new ItemStack(material, Math.max(1, amount));
    }

    private static ItemStack barrier(int amount) {
        return new ItemStack(Material.BARRIER, Math.max(1, amount));
    }

    private static ItemComponentParser.ItemComponents formatComponents(ItemComponentParser.ItemComponents components,
            Map<String, ?> replacements) {
        ItemComponentParser.ItemComponents base = components == null ? ItemComponentParser.empty() : components;
        // 跳过空 replacements 的无意义 stream 处理
        List<String> lore = (replacements == null || replacements.isEmpty())
                ? base.lore()
                : base.lore().stream().map(line -> Texts.formatTemplate(line, replacements)).toList();
        // 空 enchantments 直接复用，避免无意义的 Map 拷贝
        Map<String, Integer> enchantments = base.enchantments().isEmpty()
                ? base.enchantments()
                : new LinkedHashMap<>(base.enchantments());
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
