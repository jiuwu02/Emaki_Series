package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import emaki.jiuwu.craft.skills.api.SkillActionErrorType;
import emaki.jiuwu.craft.skills.api.SkillActionParameter;
import emaki.jiuwu.craft.skills.api.SkillActionParameterType;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

public final class DamageSkillAction extends AbstractSkillScriptAction {

    public DamageSkillAction() {
        super("damage", "combat", "Damage target entity.",
                SkillActionParameter.required("amount", SkillActionParameterType.DOUBLE, "Damage amount"),
                SkillActionParameter.optional("target", SkillActionParameterType.STRING, "target", "Target"),
                SkillActionParameter.optional("damage_type", SkillActionParameterType.STRING, "generic", "Damage type"),
                SkillActionParameter.optional("element", SkillActionParameterType.STRING, "", "Element"));
    }

    @Override
    public CompletableFuture<SkillActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        Entity entity = entityTarget(context, arguments);
        if (!(entity instanceof LivingEntity living)) {
            return completed(SkillActionResult.failure(SkillActionErrorType.INVALID_ARGUMENT, "Damage target is not a living entity."));
        }
        living.damage(Math.max(0D, doubleArg(arguments, "amount", 0D)), context.caster());
        return completed(SkillActionResult.ok());
    }
}
