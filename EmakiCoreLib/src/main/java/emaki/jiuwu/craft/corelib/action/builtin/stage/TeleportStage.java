package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.v2.compile.ValueParsers;
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

/**
 * Teleports the target to a coordinate.
 *
 * <p>Widened from v1's {@code Player} to any {@code Entity}, so {@code nearby | teleport ...} can move mobs.</p>
 *
 * <p>This stage keeps its coordinate arguments, unlike {@code spawn_particle} or {@code drop_item}. The
 * distinction is what the target flow means: here the target is <em>who moves</em> and the coordinates are
 * <em>where to</em>, so the two are different pieces of information rather than duplicates. Coordinates are
 * relative to the entity being moved, which is why {@code teleport y=~5} lifts each target by five.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: moving an entity is an operation on that entity.</p>
 */
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
            return CoreActionOutcome.skipped("action.v2.stage.common.not_entity");
        }
        Location base = target.getLocation();
        World world = StageSupport.world(arguments.getString("world"), base.getWorld());
        if (world == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.teleport.world_not_found",
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
                    "action.v2.stage.teleport.rejected");
        }
        return CoreActionOutcome.success(Map.of(
                "world", world.getName(),
                "x", destination.getX(),
                "y", destination.getY(),
                "z", destination.getZ()));
    }
}
