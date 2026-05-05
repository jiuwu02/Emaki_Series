package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.Particle;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.skills.script.SkillScriptContext;

public final class ParticleSkillAction extends AbstractSkillScriptAction {

    public ParticleSkillAction() {
        super("particle", "feedback", "Spawn skill particle.",
                ActionParameter.required("particle", ActionParameterType.STRING, "Particle"),
                ActionParameter.optional("at", ActionParameterType.STRING, "target", "Location"),
                ActionParameter.optional("count", ActionParameterType.INTEGER, "1", "Count"),
                ActionParameter.optional("speed", ActionParameterType.DOUBLE, "0", "Speed"));
    }

    @Override
    public CompletableFuture<ActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        Particle particle = ActionParsers.parseParticle(arg(arguments, "particle", ""));
        if (particle == null) {
            return completed(ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown particle: " + arg(arguments, "particle", "")));
        }
        Location location = locationTarget(context, arguments);
        if (location == null || location.getWorld() == null) {
            return completed(ActionResult.ok());
        }
        location.getWorld().spawnParticle(particle, location, intArg(arguments, "count", 1), 0D, 0D, 0D, doubleArg(arguments, "speed", 0D));
        return completed(ActionResult.ok());
    }
}
