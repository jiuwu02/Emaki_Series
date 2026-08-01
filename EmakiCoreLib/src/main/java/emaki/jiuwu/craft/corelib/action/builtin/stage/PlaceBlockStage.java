package emaki.jiuwu.craft.corelib.action.builtin.stage;

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
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;

/**
 * Places a vanilla or supported custom block at the target position.
 *
 * <p>Keeps v1's event etiquette verbatim: {@code BlockCanBuildEvent} then {@code BlockPlaceEvent} when a player
 * is available, with the previous {@code BlockState} restored if either is refused. Protection plugins therefore
 * still get their say. The player comes from the caster, so a console-triggered pipeline places without events
 * exactly as v1 did.</p>
 *
 * <p>The four coordinate arguments are gone; the position is the target.</p>
 *
 * <p>Domain {@code LOCATION_REGION}: reads and writes one block.</p>
 */
public final class PlaceBlockStage extends BaseStage {

    private final ItemSourceService itemSourceService;
    private final CraftEngineBlockBridge craftEngineBlockBridge;
    private final CustomBlockBridge itemsAdderBlockBridge;
    private final CustomBlockBridge nexoBlockBridge;
    private final CustomBlockBridge oraxenBlockBridge;

    public PlaceBlockStage(ItemSourceService itemSourceService,
            CraftEngineBlockBridge craftEngineBlockBridge,
            CustomBlockBridge itemsAdderBlockBridge,
            CustomBlockBridge nexoBlockBridge,
            CustomBlockBridge oraxenBlockBridge) {
        super("place_block", "world", "Places a vanilla or custom block at the target position.",
                CoreTargetRequirement.REQUIRED_ANY, CoreActionExecutionDomain.LOCATION_REGION,
                CoreStageParameter.optional("item_source", CoreStageParameterType.STRING, "",
                        "Block item source"));
        this.itemSourceService = itemSourceService;
        this.craftEngineBlockBridge = craftEngineBlockBridge;
        this.itemsAdderBlockBridge = itemsAdderBlockBridge;
        this.nexoBlockBridge = nexoBlockBridge;
        this.oraxenBlockBridge = oraxenBlockBridge;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        ItemSource source = StageSupport.itemSource(arguments.getString("item_source"));
        if (source == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.item.invalid_item_source",
                    Map.of("item_source", arguments.getString("item_source")));
        }
        Location location = context.currentTarget().location();
        if (location == null || location.getWorld() == null) {
            return CoreActionOutcome.skipped("action.stage.common.no_location");
        }
        Block target = location.getBlock();
        Player player = StageSupport.player(context.caster());
        return switch (source.getType()) {
            case VANILLA -> placeVanilla(player, target, source);
            case CRAFTENGINE -> placeCustom(player, target, source, craftEngineBlockBridge, "CraftEngine");
            case ITEMSADDER -> placeCustom(player, target, source, itemsAdderBlockBridge, "ItemsAdder");
            case NEXO -> placeCustom(player, target, source, nexoBlockBridge, "Nexo");
            case ORAXEN -> placeCustom(player, target, source, oraxenBlockBridge, "Oraxen");
            default -> CoreActionOutcome.skipped("action.stage.place_block.not_placeable");
        };
    }

    private CoreActionOutcome placeVanilla(Player player, Block target, ItemSource source) {
        Material material = ItemSourceUtil.resolveVanillaMaterial(source.getIdentifier());
        if (material == null || !material.isBlock() || material.isAir()) {
            return CoreActionOutcome.skipped("action.stage.place_block.not_placeable");
        }
        BlockData blockData = material.createBlockData();
        boolean canPlace = target.canPlace(blockData);
        CoreActionOutcome canBuild = callCanBuildEvent(player, target, blockData, canPlace);
        if (canBuild != null) {
            return canBuild;
        }
        BlockState replaced = target.getState();
        target.setBlockData(blockData, true);
        CoreActionOutcome placeRefused = callPlaceEvent(player, target, replaced,
                eventItem(source, material), true);
        if (placeRefused != null) {
            replaced.update(true, false);
            return placeRefused;
        }
        return placed(source, target);
    }

    private CoreActionOutcome placeCustom(Player player,
            Block target,
            ItemSource source,
            CustomBlockBridge bridge,
            String providerName) {
        if (bridge == null || !bridge.available()) {
            return CoreActionOutcome.skipped("action.stage.place_block.provider_unavailable");
        }
        boolean replaceable = target.isEmpty() || target.isLiquid() || target.isPassable();
        if (player == null && !replaceable) {
            return CoreActionOutcome.skipped("action.stage.place_block.not_replaceable");
        }
        BlockState replaced = target.getState();
        CoreActionOutcome placeRefused = callPlaceEvent(player, target, replaced,
                eventItem(source, null), replaceable);
        if (placeRefused != null) {
            return placeRefused;
        }
        if (!bridge.placeBlock(target, source.getIdentifier())) {
            replaced.update(true, false);
            return CoreActionOutcome.skipped("action.stage.place_block.provider_refused");
        }
        return placed(source, target);
    }

    /** {@return a refusal outcome, or {@code null} when the build is allowed} */
    private CoreActionOutcome callCanBuildEvent(Player player,
            Block target,
            BlockData blockData,
            boolean canPlace) {
        if (player == null) {
            return canPlace ? null : CoreActionOutcome.skipped("action.stage.place_block.cannot_place");
        }
        BlockCanBuildEvent event = new BlockCanBuildEvent(target, player, blockData, canPlace);
        Bukkit.getPluginManager().callEvent(event);
        return event.isBuildable() ? null : CoreActionOutcome.skipped("action.stage.place_block.build_denied");
    }

    /** {@return a refusal outcome, or {@code null} when the placement is allowed} */
    private CoreActionOutcome callPlaceEvent(Player player,
            Block target,
            BlockState replaced,
            ItemStack itemStack,
            boolean canBuild) {
        if (player == null) {
            return null;
        }
        Block against = target.getY() > target.getWorld().getMinHeight()
                ? target.getRelative(BlockFace.DOWN)
                : target;
        BlockPlaceEvent event = new BlockPlaceEvent(target, replaced, against, itemStack, player, canBuild,
                EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled() && event.canBuild()
                ? null
                : CoreActionOutcome.skipped("action.stage.place_block.place_cancelled");
    }

    private ItemStack eventItem(ItemSource source, Material fallback) {
        ItemStack created = itemSourceService == null ? null : itemSourceService.createItem(source, 1);
        if (!StageSupport.isEmpty(created)) {
            return created;
        }
        return new ItemStack(fallback != null && fallback.isItem() ? fallback : Material.STONE);
    }

    private static CoreActionOutcome placed(ItemSource source, Block block) {
        return CoreActionOutcome.success(Map.of(
                "item_source", StageSupport.shorthand(source),
                "world", block.getWorld().getName(),
                "x", block.getX(),
                "y", block.getY(),
                "z", block.getZ()));
    }
}
