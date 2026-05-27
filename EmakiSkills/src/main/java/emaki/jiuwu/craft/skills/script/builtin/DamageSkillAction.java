package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

public final class DamageSkillAction extends AbstractSkillScriptAction {

    public DamageSkillAction() {
        super("damage", "combat", "Damage target entity.",
                ActionParameter.required("amount", ActionParameterType.DOUBLE, "Damage amount"),
                ActionParameter.optional("target", ActionParameterType.STRING, "target", "Target"),
                ActionParameter.optional("damage_type", ActionParameterType.STRING, "generic", "Damage type"),
                ActionParameter.optional("element", ActionParameterType.STRING, "", "Element"));
    }

    @Override
    public CompletableFuture<ActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        Entity entity = entityTarget(context, arguments);
        if (!(entity instanceof LivingEntity living)) {
            return completed(ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Damage target is not a living entity."));
        }
        living.damage(Math.max(0D, doubleArg(arguments, "amount", 0D)), context.caster());
        return completed(ActionResult.ok());
    }
}
