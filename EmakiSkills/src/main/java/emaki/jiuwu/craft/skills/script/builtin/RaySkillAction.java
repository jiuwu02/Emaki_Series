package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;

import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

public final class RaySkillAction extends AbstractSkillScriptAction {

    public RaySkillAction() {
        super("ray", "target", "Select target by ray trace.",
                ActionParameter.optional("range", ActionParameterType.DOUBLE, "16", "Range"),
                ActionParameter.optional("width", ActionParameterType.DOUBLE, "1", "Width"),
                ActionParameter.optional("save", ActionParameterType.STRING, "target", "State key"));
    }

    @Override
    public CompletableFuture<ActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        double range = doubleArg(arguments, "range", 16D);
        double width = doubleArg(arguments, "width", 1D);
        Location eye = context.caster().getEyeLocation();
        RayTraceResult result = context.caster().getWorld().rayTrace(eye,
                eye.getDirection(),
                range,
                FluidCollisionMode.NEVER,
                true,
                width,
                entity -> entity instanceof LivingEntity && !entity.equals(context.caster()) && !entity.isDead());
        Entity hit = result == null ? null : result.getHitEntity();
        if (hit != null) {
            context.setTarget(hit);
            context.putSharedValue(arg(arguments, "save", "target"), hit);
        } else {
            context.setTarget(null);
        }
        return completed(ActionResult.ok(Map.of("has_target", hit != null)));
    }
}
