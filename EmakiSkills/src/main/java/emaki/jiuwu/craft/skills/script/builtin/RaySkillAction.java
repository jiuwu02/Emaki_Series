package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;

import emaki.jiuwu.craft.skills.api.SkillActionParameter;
import emaki.jiuwu.craft.skills.api.SkillActionParameterType;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

public final class RaySkillAction extends AbstractSkillScriptAction {

    public RaySkillAction() {
        super("ray", "target", "Select target by ray trace.",
                SkillActionParameter.optional("range", SkillActionParameterType.DOUBLE, "16", "Range"),
                SkillActionParameter.optional("width", SkillActionParameterType.DOUBLE, "1", "Width"),
                SkillActionParameter.optional("save", SkillActionParameterType.STRING, "target", "State key"));
    }

    @Override
    public CompletableFuture<SkillActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
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
        return completed(SkillActionResult.ok(Map.of("has_target", hit != null)));
    }
}
