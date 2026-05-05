package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Entity;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.skills.script.SkillScriptContext;

public final class IgniteSkillAction extends AbstractSkillScriptAction {

    public IgniteSkillAction() {
        super("ignite", "combat", "Ignite target entity.",
                ActionParameter.required("ticks", ActionParameterType.INTEGER, "Fire ticks"),
                ActionParameter.optional("target", ActionParameterType.STRING, "target", "Target"));
    }

    @Override
    public CompletableFuture<ActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        Entity entity = entityTarget(context, arguments);
        if (entity == null) {
            return completed(ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Ignite target not found."));
        }
        entity.setFireTicks(Math.max(entity.getFireTicks(), intArg(arguments, "ticks", 0)));
        return completed(ActionResult.ok());
    }
}
