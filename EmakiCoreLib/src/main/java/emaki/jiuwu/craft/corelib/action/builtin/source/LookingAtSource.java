package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;

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
            return CoreSourceResult.empty("action.source.looking_at.no_living_caster");
        }
        double range = arguments.getDouble("range", 5D);
        double width = arguments.getDouble("width", 0.5D);
        if (range <= 0D || width < 0D) {
            return CoreSourceResult.invalid("action.source.looking_at.invalid_ray");
        }
        Location eye = caster.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) {
            return CoreSourceResult.empty("action.source.looking_at.no_hit");
        }

        RayTraceResult result = world.rayTraceEntities(eye, eye.getDirection(), range, width,
                candidate -> candidate != null && !candidate.equals(caster));
        if (result == null || result.getHitEntity() == null) {
            return CoreSourceResult.empty("action.source.looking_at.no_hit");
        }
        return CoreSourceResult.selected(List.of(CoreActionSubject.of(result.getHitEntity())));
    }
}
