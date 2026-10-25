package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import java.util.Map;

public final class SpawnParticleAtStage extends BaseStage {

    public SpawnParticleAtStage() {
        super("spawn_particle_at", "visual", "在指定坐标生成粒子效果。",
                CoreTargetRequirement.NONE, CoreActionExecutionDomain.LOCATION_REGION,
                CoreStageParameter.required("particle", CoreStageParameterType.STRING, "粒子类型"),
                CoreStageParameter.optional("x", CoreStageParameterType.DOUBLE, "0", "X 偏移"),
                CoreStageParameter.optional("y", CoreStageParameterType.DOUBLE, "0", "Y 偏移"),
                CoreStageParameter.optional("z", CoreStageParameterType.DOUBLE, "0", "Z 偏移"),
                CoreStageParameter.optional("count", CoreStageParameterType.INTEGER, "1", "粒子数量"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        World world;
        Location origin;
        try {
            origin = context.origin();
            world = origin == null ? null : origin.getWorld();
        } catch (IllegalStateException exception) {
            return CoreActionOutcome.skipped("action.stage.spawn_particle_at.no_origin");
        }
        if (origin == null || world == null) {
            return CoreActionOutcome.skipped("action.stage.spawn_particle_at.no_origin");
        }
        Particle particle;
        try {
            particle = Particle.valueOf(
                    Texts.trim(arguments.getString("particle"))
                            .replace("minecraft:", "")
                            .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return CoreActionOutcome.failure(
                    CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.spawn_particle_at.unknown_particle",
                    Map.of("particle", arguments.getString("particle")));
        }
        double x = arguments.getDouble("x", 0D);
        double y = arguments.getDouble("y", 0D);
        double z = arguments.getDouble("z", 0D);
        int count = Math.max(1, arguments.getInt("count", 1));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "execute in " + world.getKey() + " run particle " + particle.getKey()
                        + " " + (origin.getX() + x) + " " + (origin.getY() + y) + " " + (origin.getZ() + z)
                        + " 0 0 0 0 " + count + " force");
        return CoreActionOutcome.success();
    }
}
