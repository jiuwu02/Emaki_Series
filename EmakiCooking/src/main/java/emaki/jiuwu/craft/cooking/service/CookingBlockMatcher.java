package emaki.jiuwu.craft.cooking.service;

import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationType;
import org.bukkit.block.Block;

import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import org.bukkit.Material;

public final class CookingBlockMatcher {

    private final CookingSettingsService settingsService;
    private final CraftEngineBlockBridge craftEngineBlockBridge;
    private final CustomBlockBridge itemsAdderBlockBridge;
    private final CustomBlockBridge nexoBlockBridge;
    private final CustomBlockBridge oraxenBlockBridge;

    public CookingBlockMatcher(CookingSettingsService settingsService,
            CraftEngineBlockBridge craftEngineBlockBridge,
            CustomBlockBridge itemsAdderBlockBridge,
            CustomBlockBridge nexoBlockBridge,
            CustomBlockBridge oraxenBlockBridge) {
        this.settingsService = settingsService;
        this.craftEngineBlockBridge = craftEngineBlockBridge;
        this.itemsAdderBlockBridge = itemsAdderBlockBridge;
        this.nexoBlockBridge = nexoBlockBridge;
        this.oraxenBlockBridge = oraxenBlockBridge;
    }

    public boolean matches(StationInteraction interaction, StationType stationType) {
        if (interaction == null) {
            return false;
        }
        return matches(interaction.block(), stationType, interaction.stationSource());
    }

    public boolean matches(StationBreakContext context, StationType stationType) {
        if (context == null) {
            return false;
        }
        return matches(context.block(), stationType, context.stationSource());
    }

    public boolean matches(Block block, StationType stationType) {
        return matches(block, stationType, null);
    }

    public boolean matches(Block block, StationType stationType, ItemSourceRef stationSource) {
        if (stationType == null) {
            return false;
        }
        for (ItemSourceRef source : settingsService.stationBlockSources(stationType)) {
            if (matches(block, source) || sourceMatches(source, stationSource)) {
                return true;
            }
        }
        return false;
    }

    public boolean matches(Block block, ItemSourceRef source) {
        if (block == null || source == null) {
            return false;
        }
        // An if-chain rather than a switch: the kind is a record now, so it cannot be a switch label.
        // Only these five kinds have a block form; anything else is item-only and never matches a block.
        ItemSourceKind kind = source.kind();
        if (ItemSourceKind.VANILLA.equals(kind)) {
            return matchesVanilla(block, source.identifier());
        }
        if (ItemSourceKind.CRAFTENGINE.equals(kind)) {
            return craftEngineBlockBridge != null && craftEngineBlockBridge.matches(block, source.identifier());
        }
        if (ItemSourceKind.ITEMSADDER.equals(kind)) {
            return itemsAdderBlockBridge != null && itemsAdderBlockBridge.matches(block, source.identifier());
        }
        if (ItemSourceKind.NEXO.equals(kind)) {
            return nexoBlockBridge != null && nexoBlockBridge.matches(block, source.identifier());
        }
        if (ItemSourceKind.ORAXEN.equals(kind)) {
            return oraxenBlockBridge != null && oraxenBlockBridge.matches(block, source.identifier());
        }
        return false;
    }

    private boolean sourceMatches(ItemSourceRef expected, ItemSourceRef actual) {
        return ItemSourceUtil.matches(expected, actual);
    }

    public boolean place(Block block, ItemSourceRef source) {
        if (block == null || source == null) {
            return false;
        }
        ItemSourceKind kind = source.kind();
        if (ItemSourceKind.VANILLA.equals(kind)) {
            return placeVanilla(block, source.identifier());
        }
        if (ItemSourceKind.CRAFTENGINE.equals(kind)) {
            return craftEngineBlockBridge != null && craftEngineBlockBridge.placeBlock(block, source.identifier());
        }
        if (ItemSourceKind.ITEMSADDER.equals(kind)) {
            return itemsAdderBlockBridge != null && itemsAdderBlockBridge.placeBlock(block, source.identifier());
        }
        if (ItemSourceKind.NEXO.equals(kind)) {
            return nexoBlockBridge != null && nexoBlockBridge.placeBlock(block, source.identifier());
        }
        if (ItemSourceKind.ORAXEN.equals(kind)) {
            return oraxenBlockBridge != null && oraxenBlockBridge.placeBlock(block, source.identifier());
        }
        return false;
    }

    public boolean setCustomLit(Block block, boolean lit) {
        if (block == null) {
            return false;
        }
        if (craftEngineBlockBridge != null && craftEngineBlockBridge.isCustomBlock(block)) {
            return craftEngineBlockBridge.setLit(block, lit);
        }
        if (itemsAdderBlockBridge != null && itemsAdderBlockBridge.isCustomBlock(block)) {
            return itemsAdderBlockBridge.setLit(block, lit);
        }
        if (nexoBlockBridge != null && nexoBlockBridge.isCustomBlock(block)) {
            return nexoBlockBridge.setLit(block, lit);
        }
        if (oraxenBlockBridge != null && oraxenBlockBridge.isCustomBlock(block)) {
            return oraxenBlockBridge.setLit(block, lit);
        }
        return false;
    }

    private boolean matchesVanilla(Block block, String identifier) {
        Material expected = resolveMaterial(identifier);
        return expected != null && block.getType() == expected;
    }

    private boolean placeVanilla(Block block, String identifier) {
        Material material = resolveMaterial(identifier);
        if (material == null || material == Material.AIR) {
            return false;
        }
        block.setType(material);
        return block.getType() == material;
    }

    private Material resolveMaterial(String identifier) {
        return ItemSourceUtil.resolveVanillaMaterial(identifier);
    }
}
