package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

public final class SetWorldSpawnStage extends BaseStage {

    public SetWorldSpawnStage() {
        super("set_world_spawn", "world", "把世界出生点设置为管道原点。",
                CoreTargetRequirement.NONE, CoreActionExecutionDomain.LOCATION_REGION);
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Location origin;
        World world;
        try {
            origin = context.origin();
            world = origin == null ? null : origin.getWorld();
        } catch (IllegalStateException exception) {
            return CoreActionOutcome.skipped("action.stage.set_world_spawn.no_origin");
        }
        if (origin == null || world == null) {
            return CoreActionOutcome.skipped("action.stage.set_world_spawn.no_origin");
        }
        world.setSpawnLocation(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
        return CoreActionOutcome.success();
    }
}
