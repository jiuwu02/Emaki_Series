package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.ValueParsers;
import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

/**
 * Spawns particles at the target's position.
 *
 * <p>This is the clearest case of the pipeline removing arguments. v1 carried {@code target}, {@code world},
 * {@code x}, {@code y} and {@code z} and re-implemented location resolution inside the action; all five are
 * gone, because "where" is now the target flow's job. {@code at x=.. y=.. z=.. | spawn_particle ...} and
 * {@code nearby radius=5 | spawn_particle ...} both work without the stage knowing how the position was
 * chosen.</p>
 *
 * <p>Domain {@code LOCATION_REGION}: {@code World#spawnParticle} is a region write.</p>
 */
public final class SpawnParticleStage extends BaseStage {

    public SpawnParticleStage() {
        super("spawn_particle", "feedback", "Spawns particles at the target position.",
                CoreTargetRequirement.REQUIRED_ANY, CoreActionExecutionDomain.LOCATION_REGION,
                CoreStageParameter.required("particle", CoreStageParameterType.STRING, "Particle key"),
                CoreStageParameter.optional("count", CoreStageParameterType.INTEGER, "1", "Particle count"),
                CoreStageParameter.optional("offset_x", CoreStageParameterType.DOUBLE, "0", "Offset x"),
                CoreStageParameter.optional("offset_y", CoreStageParameterType.DOUBLE, "0", "Offset y"),
                CoreStageParameter.optional("offset_z", CoreStageParameterType.DOUBLE, "0", "Offset z"),
                CoreStageParameter.optional("extra", CoreStageParameterType.DOUBLE, "0", "Extra data"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        String key = arguments.getString("particle");
        Particle particle = ValueParsers.parseParticle(key);
        if (particle == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.spawn_particle.unknown_particle", Map.of("particle", key));
        }
        Location location = context.currentTarget().location();
        if (location == null || location.getWorld() == null) {
            return CoreActionOutcome.skipped("action.stage.common.no_location");
        }
        World world = location.getWorld();
        world.spawnParticle(particle, location,
                Math.max(0, arguments.getInt("count", 1)),
                arguments.getDouble("offset_x", 0D),
                arguments.getDouble("offset_y", 0D),
                arguments.getDouble("offset_z", 0D),
                arguments.getDouble("extra", 0D));
        return CoreActionOutcome.success();
    }
}
