package emaki.jiuwu.craft.corelib.action.builtin.source;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;

public final class HighestBlockSource extends BaseSource {

    public HighestBlockSource() {
        super("highest_block", "管道原点所在列的最高方块。",
                CoreActionExecutionDomain.LOCATION_REGION);
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Location centre;
        try {
            centre = context.origin();
        } catch (IllegalStateException exception) {
            return CoreSourceResult.empty("action.source.highest_block.no_origin");
        }
        if (centre == null || centre.getWorld() == null) {
            return CoreSourceResult.empty("action.source.highest_block.no_origin");
        }
        World world = centre.getWorld();
        Block block = world.getHighestBlockAt(centre.getBlockX(), centre.getBlockZ());
        if (block == null) {
            return CoreSourceResult.empty("action.source.highest_block.no_block");
        }
        return CoreSourceResult.selected(java.util.List.of(CoreActionSubject.of(block.getLocation())));
    }
}
