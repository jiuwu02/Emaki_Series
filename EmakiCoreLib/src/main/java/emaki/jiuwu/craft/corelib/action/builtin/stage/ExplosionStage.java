package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

/**
 * Creates an explosion at the target position.
 *
 * <p>{@code fire} and {@code break_blocks} both default to {@code false}, so the explosion is cosmetic and
 * damaging rather than terrain-altering unless asked otherwise. Zero power is {@code Skipped}: an explosion with
 * no power is a no-op, not a failure.</p>
 *
 * <p>Domain {@code LOCATION_REGION}: an explosion affects blocks and entities in a region.</p>
 */
public final class ExplosionStage extends BaseStage {

    public ExplosionStage() {
        super("explosion", "world", "Creates an explosion at the target position.",
                CoreTargetRequirement.REQUIRED_ANY, CoreActionExecutionDomain.LOCATION_REGION,
                CoreStageParameter.optional("power", CoreStageParameterType.DOUBLE, "0", "Explosion power"),
                CoreStageParameter.optional("fire", CoreStageParameterType.BOOLEAN, "false", "Set fire"),
                CoreStageParameter.optional("break_blocks", CoreStageParameterType.BOOLEAN, "false",
                        "Break blocks"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Location location = context.currentTarget().location();
        if (location == null || location.getWorld() == null) {
            return CoreActionOutcome.skipped("action.stage.common.no_location");
        }
        double power = Math.max(0D, arguments.getDouble("power", 0D));
        if (power <= 0D) {
            return CoreActionOutcome.skipped("action.stage.explosion.zero_power");
        }
        boolean fire = arguments.getBoolean("fire", false);
        boolean breakBlocks = arguments.getBoolean("break_blocks", false);
        World world = location.getWorld();
        boolean created = world.createExplosion(location.getX(), location.getY(), location.getZ(),
                (float) power, fire, breakBlocks);
        return CoreActionOutcome.success(Map.of(
                "created", created,
                "power", power,
                "fire", fire,
                "break_blocks", breakBlocks));
    }
}
