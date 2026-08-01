package emaki.jiuwu.craft.corelib.action.builtin.v2.source;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseSource;
import emaki.jiuwu.craft.corelib.action.builtin.v2.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;

/**
 * The entity the caster is looking at.
 *
 * <p>Replaces the Skills-side {@code ray} action; its {@code save=target} write-back is now the explicit
 * {@code keep} gate instead of a hidden context side effect.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: {@code LivingEntity#rayTraceEntities} reads the caster's position and
 * direction plus the surrounding entities, so it must run on the thread that owns the caster.</p>
 */
public final class LookingAtSource extends BaseSource {

    public LookingAtSource() {
        super("looking_at", "The entity the caster is looking at.",
                CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.optional("range", CoreStageParameterType.DOUBLE, "5", "Ray length"),
                CoreStageParameter.optional("width", CoreStageParameterType.DOUBLE, "0.5", "Ray width"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        LivingEntity caster = StageSupport.livingEntity(context.caster());
        if (caster == null) {
            return CoreSourceResult.empty("action.v2.source.looking_at.no_living_caster");
        }
        double range = arguments.getDouble("range", 5D);
        double width = arguments.getDouble("width", 0.5D);
        if (range <= 0D || width < 0D) {
            return CoreSourceResult.invalid("action.v2.source.looking_at.invalid_ray");
        }
        Location eye = caster.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) {
            return CoreSourceResult.empty("action.v2.source.looking_at.no_hit");
        }
        // World#rayTraceEntities rather than LivingEntity#rayTraceEntities: only the former accepts a ray
        // size, and `width` has to keep the meaning the Skills-side `ray` action gave it.
        RayTraceResult result = world.rayTraceEntities(eye, eye.getDirection(), range, width,
                candidate -> candidate != null && !candidate.equals(caster));
        if (result == null || result.getHitEntity() == null) {
            return CoreSourceResult.empty("action.v2.source.looking_at.no_hit");
        }
        return CoreSourceResult.selected(List.of(CoreActionSubject.of(result.getHitEntity())));
    }
}
