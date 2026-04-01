package emaki.jiuwu.craft.forge.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;
import net.kyori.adventure.text.Component;

final class ForgeResultItemFactory {

    private final EmakiForgePlugin plugin;
    private final ForgeLayerSnapshotBuilder snapshotBuilder;

    ForgeResultItemFactory(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        this.snapshotBuilder = new ForgeLayerSnapshotBuilder(plugin);
    }

    ItemStack createResultItem(Recipe recipe,
            GuiItems guiItems,
            double multiplier,
            QualitySettings.QualityTier qualityTier,
            long forgedAt) {
        EmakiItemAssemblyRequest request = buildAssemblyRequest(recipe, guiItems, multiplier, qualityTier, forgedAt);
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        return coreLib == null || request == null ? null : coreLib.itemAssemblyService().preview(request);
    }

    EmakiItemAssemblyRequest buildAssemblyRequest(Recipe recipe,
            GuiItems guiItems,
            double multiplier,
            QualitySettings.QualityTier qualityTier,
            long forgedAt) {
        if (recipe == null || recipe.result() == null || recipe.result().outputItem() == null) {
            return null;
        }
        EmakiItemLayerSnapshot forgeLayer = snapshotBuilder.buildLayerSnapshot(recipe, guiItems, multiplier, qualityTier, forgedAt);
        ItemStack existingItem = guiItems == null ? null : guiItems.targetItem();
        return new EmakiItemAssemblyRequest(
                recipe.result().outputItem(),
                1,
                existingItem,
                java.util.List.of(forgeLayer)
        );
    }

    String resolveResultItemName(Recipe recipe, ItemStack itemStack) {
        String resolvedItemName = resolveItemName(itemStack);
        if (Texts.isNotBlank(resolvedItemName)) {
            return resolvedItemName;
        }
        if (recipe != null && recipe.result() != null && recipe.result().outputItem() != null) {
            return recipe.result().outputItem().getIdentifier();
        }
        return "物品";
    }

    String resolveSourceItemName(GuiItems guiItems, ItemStack resultItem, Recipe recipe) {
        String sourceName = resolveItemName(guiItems == null ? null : guiItems.targetItem());
        return Texts.isNotBlank(sourceName) ? sourceName : resolveResultItemName(recipe, resultItem);
    }

    String buildShowItemPlaceholder(GuiItems guiItems, Recipe recipe, ItemStack resultItem) {
        if (resultItem == null || resultItem.getType() == Material.AIR) {
            return resolveSourceItemName(guiItems, resultItem, recipe);
        }
        Component display = resolveDisplayComponent(guiItems == null ? null : guiItems.targetItem());
        if (display == null) {
            display = resolveDisplayComponent(resultItem);
        }
        if (display == null) {
            display = MiniMessages.parse(resolveResultItemName(recipe, resultItem));
        }
        try {
            return MiniMessages.serialize(display.hoverEvent(resultItem.asHoverEvent(showItem -> showItem)));
        } catch (Exception ignored) {
            return resolveSourceItemName(guiItems, resultItem, recipe);
        }
    }

    private String resolveItemName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return "";
        }
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasCustomName()) {
            return MiniMessages.plain(itemStack.getItemMeta().customName());
        }
        try {
            return MiniMessages.plain(itemStack.effectiveName());
        } catch (Exception ignored) {
            return itemStack.getType().name();
        }
    }

    private Component resolveDisplayComponent(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasCustomName()) {
            return itemStack.getItemMeta().customName();
        }
        try {
            return itemStack.effectiveName();
        } catch (Exception ignored) {
            return Component.text(itemStack.getType().name());
        }
    }
}
