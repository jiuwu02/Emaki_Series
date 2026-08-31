package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.ValueParsers;
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

public final class TeleportStage extends BaseStage {

    public TeleportStage() {
        super("teleport", "entity", "Teleports the target to a coordinate.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.optional("world", CoreStageParameterType.STRING, "",
                        "Destination world, defaults to the target's own"),
                CoreStageParameter.optional("x", CoreStageParameterType.STRING, "~", "X, supports ~"),
                CoreStageParameter.optional("y", CoreStageParameterType.STRING, "~", "Y, supports ~"),
                CoreStageParameter.optional("z", CoreStageParameterType.STRING, "~", "Z, supports ~"),
                CoreStageParameter.optional("yaw", CoreStageParameterType.DOUBLE, "", "Yaw"),
                CoreStageParameter.optional("pitch", CoreStageParameterType.DOUBLE, "", "Pitch"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Entity target = StageSupport.entity(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_entity");
        }
        Location base = target.getLocation();
        World world = StageSupport.world(arguments.getString("world"), base.getWorld());
        if (world == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.teleport.world_not_found",
                    Map.of("world", arguments.getString("world")));
        }
        Location destination = new Location(world,
                ValueParsers.parseCoordinate(arguments.getString("x", "~"), base.getX()),
                ValueParsers.parseCoordinate(arguments.getString("y", "~"), base.getY()),
                ValueParsers.parseCoordinate(arguments.getString("z", "~"), base.getZ()),
                (float) arguments.getDouble("yaw", base.getYaw()),
                (float) arguments.getDouble("pitch", base.getPitch()));
        if (!target.teleport(destination)) {
            return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.stage.teleport.rejected");
        }
        return CoreActionOutcome.success(Map.of(
                "world", world.getName(),
                "x", destination.getX(),
                "y", destination.getY(),
                "z", destination.getZ()));
    }
}
