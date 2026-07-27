package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class PlaceBlockAction extends LocationTargetAction {

    private final ItemSourceService itemSourceService;
    private final CraftEngineBlockBridge craftEngineBlockBridge;
    private final CustomBlockBridge itemsAdderBlockBridge;
    private final CustomBlockBridge nexoBlockBridge;
    private final CustomBlockBridge oraxenBlockBridge;

    public PlaceBlockAction(ItemSourceService itemSourceService,
            CraftEngineBlockBridge craftEngineBlockBridge,
            CustomBlockBridge itemsAdderBlockBridge,
            CustomBlockBridge nexoBlockBridge,
            CustomBlockBridge oraxenBlockBridge) {
        super(
                "placeblock",
                "world",
                "Place a vanilla or supported custom block from an item source.",
                ActionParameter.optional("source", ActionParameterType.STRING, "", "Item source"),
                ActionParameter.required("x", ActionParameterType.STRING, "X"),
                ActionParameter.required("y", ActionParameterType.STRING, "Y"),
                ActionParameter.required("z", ActionParameterType.STRING, "Z"),
                ActionParameter.optional("world", ActionParameterType.STRING, "", "World")
        );
        this.itemSourceService = itemSourceService;
        this.craftEngineBlockBridge = craftEngineBlockBridge;
        this.itemsAdderBlockBridge = itemsAdderBlockBridge;
        this.nexoBlockBridge = nexoBlockBridge;
        this.oraxenBlockBridge = oraxenBlockBridge;
    }

    @Override
    public boolean acceptsDynamicParameter(String name) {
        return ActionItemSourceArguments.isAlias(name);
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ItemSource source = ActionItemSourceArguments.resolve(arguments);
        if (source == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Invalid item source for placeblock.");
        }
        ActionLocationResolver.ResolvedLocation resolved = ActionLocationResolver.resolve(context, arguments, id());
        if (!resolved.success()) {
            return resolved.error();
        }
        Block target = resolved.location().getBlock();
        return switch (source.getType()) {
            case VANILLA -> placeVanilla(context.player(), target, source);
            case CRAFTENGINE -> placeCustom(context.player(), target, source, craftEngineBlockBridge, "CraftEngine");
            case ITEMSADDER -> placeCustom(context.player(), target, source, itemsAdderBlockBridge, "ItemsAdder");
            case NEXO -> placeCustom(context.player(), target, source, nexoBlockBridge, "Nexo");
            case ORAXEN -> placeCustom(context.player(), target, source, oraxenBlockBridge, "Oraxen");
            default -> ActionResult.skipped("Item source '" + ItemSourceUtil.toShorthand(source) + "' is not a placeable block source.");
        };
    }

    private ActionResult placeVanilla(Player player, Block target, ItemSource source) {
        Material material = ItemSourceUtil.resolveVanillaMaterial(source.getIdentifier());
        if (material == null || !material.isBlock() || material.isAir()) {
            return ActionResult.skipped("Item source '" + ItemSourceUtil.toShorthand(source) + "' is not a placeable vanilla block.");
        }
        BlockData blockData = material.createBlockData();
        boolean canPlace = target.canPlace(blockData);
        ActionResult canBuildCheck = callCanBuildEvent(player, target, blockData, canPlace);
        if (!canBuildCheck.success() || canBuildCheck.skipped()) {
            return canBuildCheck;
        }
        BlockState replaced = target.getState();
        target.setBlockData(blockData, true);
        ActionResult placeCheck = callPlaceEvent(player, target, replaced, createEventItem(source, material), true);
        if (!placeCheck.success() || placeCheck.skipped()) {
            replaced.update(true, false);
            return placeCheck;
        }
        return placedResult(source, target.getLocation());
    }

    private ActionResult placeCustom(Player player,
            Block target,
            ItemSource source,
            CustomBlockBridge bridge,
            String providerName) {
        if (bridge == null || !bridge.available()) {
            return ActionResult.skipped(providerName + " block provider is not available.");
        }
        boolean replaceable = isTargetReplaceable(target);
        if (player == null && !replaceable) {
            return ActionResult.skipped("Target block is not replaceable.");
        }
        BlockState replaced = target.getState();
        ActionResult placeCheck = callPlaceEvent(player, target, replaced, createEventItem(source, null), replaceable);
        if (!placeCheck.success() || placeCheck.skipped()) {
            return placeCheck;
        }
        boolean placed = bridge.placeBlock(target, source.getIdentifier());
        if (!placed) {
            replaced.update(true, false);
            return ActionResult.skipped(providerName + " did not place block source '" + ItemSourceUtil.toShorthand(source) + "'.");
        }
        return placedResult(source, target.getLocation());
    }

    private ActionResult callCanBuildEvent(Player player, Block target, BlockData blockData, boolean canPlace) {
        if (player == null) {
            return canPlace ? ActionResult.ok() : ActionResult.skipped("Block cannot be placed at the target location.");
        }
        BlockCanBuildEvent event = new BlockCanBuildEvent(target, player, blockData, canPlace);
        Bukkit.getPluginManager().callEvent(event);
        return event.isBuildable()
                ? ActionResult.ok()
                : ActionResult.skipped("Block build was not allowed at the target location.");
    }

    private ActionResult callPlaceEvent(Player player,
            Block target,
            BlockState replaced,
            ItemStack itemStack,
            boolean canBuild) {
        if (player == null) {
            return ActionResult.ok();
        }
        BlockPlaceEvent event = new BlockPlaceEvent(
                target,
                replaced,
                placedAgainst(target),
                itemStack,
                player,
                canBuild,
                EquipmentSlot.HAND
        );
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled() && event.canBuild()
                ? ActionResult.ok()
                : ActionResult.skipped("Block placement was cancelled or disallowed.");
    }

    private Block placedAgainst(Block target) {
        return target.getY() > target.getWorld().getMinHeight()
                ? target.getRelative(BlockFace.DOWN)
                : target;
    }

    private boolean isTargetReplaceable(Block block) {
        return block != null && (block.isEmpty() || block.isLiquid() || block.isPassable());
    }

    private ItemStack createEventItem(ItemSource source, Material fallback) {
        ItemStack created = itemSourceService == null ? null : itemSourceService.createItem(source, 1);
        if (created != null && !created.getType().isAir()) {
            return created;
        }
        Material material = fallback != null && fallback.isItem() ? fallback : Material.STONE;
        return new ItemStack(material);
    }

    private ActionResult placedResult(ItemSource source, Location location) {
        return ActionResult.ok(Map.of(
                "source", Texts.toStringSafe(ItemSourceUtil.toShorthand(source)),
                "world", location.getWorld() == null ? "" : location.getWorld().getName(),
                "x", location.getBlockX(),
                "y", location.getBlockY(),
                "z", location.getBlockZ()
        ));
    }
}
