package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Entity;

import emaki.jiuwu.craft.skills.api.SkillActionErrorType;
import emaki.jiuwu.craft.skills.api.SkillActionParameter;
import emaki.jiuwu.craft.skills.api.SkillActionParameterType;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

public final class IgniteSkillAction extends AbstractSkillScriptAction {

    public IgniteSkillAction() {
        super("ignite", "combat", "Ignite target entity.",
                SkillActionParameter.required("ticks", SkillActionParameterType.INTEGER, "Fire ticks"),
                SkillActionParameter.optional("target", SkillActionParameterType.STRING, "target", "Target"));
    }

    @Override
    public CompletableFuture<SkillActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        Entity entity = entityTarget(context, arguments);
        if (entity == null) {
            return completed(SkillActionResult.failure(SkillActionErrorType.INVALID_ARGUMENT, "Ignite target not found."));
        }
        entity.setFireTicks(Math.max(entity.getFireTicks(), intArg(arguments, "ticks", 0)));
        return completed(SkillActionResult.ok());
    }
}
