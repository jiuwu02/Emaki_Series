package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import emaki.jiuwu.craft.corelib.action.ActionParsers;
import org.bukkit.Location;
import org.bukkit.Particle;

import emaki.jiuwu.craft.skills.api.SkillActionErrorType;
import emaki.jiuwu.craft.skills.api.SkillActionParameter;
import emaki.jiuwu.craft.skills.api.SkillActionParameterType;
import emaki.jiuwu.craft.skills.api.SkillActionResult;

import emaki.jiuwu.craft.skills.api.SkillScriptContext;

public final class ParticleSkillAction extends AbstractSkillScriptAction {

    public ParticleSkillAction() {
        super("particle", "feedback", "Spawn skill particle.",
                SkillActionParameter.required("particle", SkillActionParameterType.STRING, "Particle"),
                SkillActionParameter.optional("at", SkillActionParameterType.STRING, "target", "Location"),
                SkillActionParameter.optional("count", SkillActionParameterType.INTEGER, "1", "Count"),
                SkillActionParameter.optional("speed", SkillActionParameterType.DOUBLE, "0", "Speed"));
    }

    @Override
    public CompletableFuture<SkillActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        Particle particle = ActionParsers.parseParticle(arg(arguments, "particle", ""));
        if (particle == null) {
            return completed(SkillActionResult.failure(SkillActionErrorType.INVALID_ARGUMENT, "Unknown particle: " + arg(arguments, "particle", "")));
        }
        Location location = locationTarget(context, arguments);
        if (location == null || location.getWorld() == null) {
            return completed(SkillActionResult.ok());
        }
        location.getWorld().spawnParticle(particle, location, intArg(arguments, "count", 1), 0D, 0D, 0D, doubleArg(arguments, "speed", 0D));
        return completed(SkillActionResult.ok());
    }
}
