package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Sets the block at the target position, without firing placement events.
 *
 * <p>The v1 {@code material} / {@code block} alias pair is collapsed into {@code material}; {@code block_data}
 * still takes a full Bukkit block-data string such as {@code oak_stairs[facing=east]} and wins when both are
 * given.</p>
 *
 * <p>Unlike {@code place_block} this is a raw write with no {@code BlockPlaceEvent}, so protection plugins do not
 * see it. That is v1's behaviour and the reason the two stages both exist.</p>
 *
 * <p>Domain {@code LOCATION_REGION}: writes one block.</p>
 */
public final class SetBlockStage extends BaseStage {

    public SetBlockStage() {
        super("set_block", "world", "Sets the block at the target position.",
                CoreTargetRequirement.REQUIRED_ANY, CoreActionExecutionDomain.LOCATION_REGION,
                CoreStageParameter.optional("material", CoreStageParameterType.STRING, "", "Block material"),
                CoreStageParameter.optional("block_data", CoreStageParameterType.STRING, "",
                        "Bukkit block data string"),
                CoreStageParameter.optional("apply_physics", CoreStageParameterType.BOOLEAN, "true",
                        "Apply physics"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        BlockData blockData = resolveBlockData(arguments);
        if (blockData == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.set_block.invalid_material",
                    Map.of("material", arguments.getString("material"),
                            "block_data", arguments.getString("block_data")));
        }
        if (blockData.getMaterial().isAir() || !blockData.getMaterial().isBlock()) {
            return CoreActionOutcome.skipped("action.v2.stage.set_block.not_a_block");
        }
        Location location = context.currentTarget().location();
        if (location == null || location.getWorld() == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.no_location");
        }
        Block block = location.getBlock();
        Material before = block.getType();
        block.setBlockData(blockData, arguments.getBoolean("apply_physics", true));
        return CoreActionOutcome.success(Map.of(
                "world", block.getWorld().getName(),
                "x", block.getX(),
                "y", block.getY(),
                "z", block.getZ(),
                "before", before.name().toLowerCase(Locale.ROOT),
                "after", block.getType().name().toLowerCase(Locale.ROOT)));
    }

    private static BlockData resolveBlockData(CoreResolvedArguments arguments) {
        String rawBlockData = arguments.getString("block_data");
        if (Texts.isNotBlank(rawBlockData)) {
            try {
                return Bukkit.createBlockData(rawBlockData);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
        Material material = StageSupport.material(arguments.getString("material"));
        return material == null ? null : material.createBlockData();
    }
}
