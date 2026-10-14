package emaki.jiuwu.craft.corelib.action.builtin.source;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;

public final class VerticalOffsetBlockSource extends BaseSource {

    public VerticalOffsetBlockSource() {
        super("vertical_block", "管道原点垂直偏移后的方块位置。",
                CoreActionExecutionDomain.LOCATION_REGION,
                CoreStageParameter.optional("offset", CoreStageParameterType.INTEGER, "1",
                        "垂直偏移格数，正数向上、负数向下"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Location centre;
        try {
            centre = context.origin();
        } catch (IllegalStateException exception) {
            return CoreSourceResult.empty("action.source.vertical_block.no_origin");
        }
        if (centre == null || centre.getWorld() == null) {
            return CoreSourceResult.empty("action.source.vertical_block.no_origin");
        }
        int offset = arguments.getInt("offset", 1);
        World world = centre.getWorld();
        Location target = new Location(world,
                centre.getBlockX() + 0.5D,
                centre.getBlockY() + offset,
                centre.getBlockZ() + 0.5D);
        return CoreSourceResult.selected(java.util.List.of(CoreActionSubject.of(target)));
    }
}
