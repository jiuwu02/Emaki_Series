package emaki.jiuwu.craft.corelib.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class GuiItemBuilder {

    @FunctionalInterface
    public interface ItemFactory {

        ItemStack create(ItemSource source, int amount);

        default ConfiguredItemService configuredItemService() {
            return null;
        }
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
        ConfiguredItemDefinition definition = ItemComponentParser.toDefinition(
                request.item(),
                request.components(),
                request.amount(),
                request.replacements()
        );
        ConfiguredItemService configuredItems = itemFactory == null ? null : itemFactory.configuredItemService();
        if (configuredItems != null) {
            return build(definition, request.replacements(), configuredItems);
        }
        return buildLegacy(new Request(
                definition.source(),
                ItemComponentParser.fromDefinition(definition),
                definition.amount(),
                Map.of()
        ), itemFactory);
    }

    private static ItemStack buildLegacy(Request request, ItemFactory itemFactory) {
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

    public static ItemStack build(ConfiguredItemDefinition definition,
            Map<String, ?> replacements,
            ConfiguredItemService configuredItemService) {
        int amount = definition == null ? 1 : definition.amount();
        ItemBuildResult result = buildResult(definition, replacements, configuredItemService);
        ItemStack itemStack = result.itemStack();
        return itemStack == null || result.hasErrors() ? barrier(amount) : itemStack;
    }

    public static ItemBuildResult buildResult(ConfiguredItemDefinition definition,
            Map<String, ?> replacements,
            ConfiguredItemService configuredItemService) {
        return configuredItemService == null
                ? ItemBuildResult.unavailable("Configured item service is unavailable.")
                : configuredItemService.create(definition, replacements);
    }

    public static ItemStack apply(ItemStack baseItem,
            ConfiguredItemDefinition definition,
            Map<String, ?> replacements,
            ConfiguredItemService configuredItemService) {
        ItemBuildResult result = configuredItemService == null
                ? ItemBuildResult.unavailable("Configured item service is unavailable.")
                : configuredItemService.apply(baseItem, definition, replacements);
        ItemStack itemStack = result.itemStack();
        return itemStack == null || result.hasErrors()
                ? barrier(baseItem == null ? 1 : baseItem.getAmount())
                : itemStack;
    }

    public static ItemStack apply(ItemStack baseItem,
            ItemComponentParser.ItemComponents components,
            Map<String, ?> replacements) {
        int amount = baseItem == null ? 1 : baseItem.getAmount();
        ConfiguredItemDefinition definition = ItemComponentParser.toDefinition(
                null,
                components,
                amount,
                replacements
        );
        ItemStack itemStack = baseItem == null ? barrier(1) : baseItem.clone();
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            ItemComponentParser.apply(itemMeta, ItemComponentParser.fromDefinition(definition));
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
            case MMOITEMS, ITEMSADDER, NEIGEITEMS, NEXO, ORAXEN, CRAFTENGINE, EMAKIITEM, ECOITEMS ->
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
        Map<String, ?> safeReplacements = replacements == null ? Map.of() : replacements;
        String displayName = base.displayNameConfig() == null
                ? Texts.formatTemplate(base.displayName(), safeReplacements)
                : ExpressionEngine.evaluateStringConfig(base.displayNameConfig(), safeReplacements);
        List<String> lore = resolveLore(base, safeReplacements);
        Map<String, Integer> enchantments = base.enchantments().isEmpty()
                ? base.enchantments()
                : new LinkedHashMap<>(base.enchantments());
        return new ItemComponentParser.ItemComponents(
                displayName,
                base.loreConfigured(),
                lore,
                base.itemModel(),
                base.customModelData(),
                enchantments,
                base.hiddenComponents()
        );
    }

    private static List<String> resolveLore(ItemComponentParser.ItemComponents base, Map<String, ?> replacements) {
        if (base == null || !base.loreConfigured()) {
            return List.of();
        }
        if (base.loreConfig() != null) {
            return ExpressionEngine.evaluateStringLinesConfig(base.loreConfig(), replacements);
        }
        return base.lore().stream()
                .map(line -> Texts.formatTemplate(line, replacements))
                .toList();
    }
}
