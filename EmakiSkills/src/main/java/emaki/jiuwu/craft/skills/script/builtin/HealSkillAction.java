package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import emaki.jiuwu.craft.skills.api.SkillActionParameter;
import emaki.jiuwu.craft.skills.api.SkillActionParameterType;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

public final class HealSkillAction extends AbstractSkillScriptAction {

    public HealSkillAction() {
        super("heal", "combat", "Heal target entity.",
                SkillActionParameter.required("amount", SkillActionParameterType.DOUBLE, "Heal amount"),
                SkillActionParameter.optional("target", SkillActionParameterType.STRING, "caster", "Target"));
    }

    @Override
    public CompletableFuture<SkillActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        Entity entity = entityTarget(context, arguments);
        if (entity instanceof LivingEntity living) {
            double max = living.getAttribute(Attribute.MAX_HEALTH) == null ? living.getHealth() : living.getAttribute(Attribute.MAX_HEALTH).getValue();
            living.setHealth(Math.min(max, living.getHealth() + Math.max(0D, doubleArg(arguments, "amount", 0D))));
        }
        return completed(SkillActionResult.ok());
    }
}
