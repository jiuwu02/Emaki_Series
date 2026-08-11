package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

/**
 * Breaks or clears the block at the target position.
 *
 * <p>Fires {@code BlockBreakEvent} when the caster is a player, so protection plugins can cancel it; a
 * console-triggered pipeline breaks without the event, as in v1. With {@code drop_items} the block breaks
 * naturally using the caster's held item for drop rules, otherwise it is replaced with air.</p>
 *
 * <p>Domain {@code LOCATION_REGION}: reads and writes one block.</p>
 */
public final class BreakBlockStage extends BaseStage {

    public BreakBlockStage() {
        super("break_block", "world", "Breaks or clears the block at the target position.",
                CoreTargetRequirement.REQUIRED_ANY, CoreActionExecutionDomain.LOCATION_REGION,
                CoreStageParameter.optional("drop_items", CoreStageParameterType.BOOLEAN, "false",
                        "Drop block items"),
                CoreStageParameter.optional("apply_physics", CoreStageParameterType.BOOLEAN, "true",
                        "Apply physics when clearing"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Location location = context.currentTarget().location();
        if (location == null || location.getWorld() == null) {
            return CoreActionOutcome.skipped("action.stage.common.no_location");
        }
        Block block = location.getBlock();
        Material before = block.getType();
        if (before.isAir()) {
            return CoreActionOutcome.skipped("action.stage.break_block.already_air");
        }
        Player player = StageSupport.player(context.caster());
        if (player != null) {
            BlockBreakEvent event = new BlockBreakEvent(block, player);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return CoreActionOutcome.skipped("action.stage.break_block.cancelled");
            }
        }
        boolean dropItems = arguments.getBoolean("drop_items", false);
        boolean broken;
        if (dropItems) {
            broken = player == null
                    ? block.breakNaturally()
                    : block.breakNaturally(player.getInventory().getItemInMainHand());
        } else {
            block.setType(Material.AIR, arguments.getBoolean("apply_physics", true));
            broken = true;
        }
        if (!broken) {
            return CoreActionOutcome.skipped("action.stage.break_block.not_broken");
        }
        return CoreActionOutcome.success(Map.of(
                "world", block.getWorld().getName(),
                "x", block.getX(),
                "y", block.getY(),
                "z", block.getZ(),
                "before", before.name().toLowerCase(Locale.ROOT),
                "drop_items", dropItems));
    }
}
