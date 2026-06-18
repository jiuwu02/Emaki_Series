package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import emaki.jiuwu.craft.skills.api.SkillActionErrorType;
import emaki.jiuwu.craft.skills.api.SkillActionParameter;
import emaki.jiuwu.craft.skills.api.SkillActionParameterType;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;
import emaki.jiuwu.craft.skills.mythic.MythicSkillCastService;
import emaki.jiuwu.craft.skills.trigger.TriggerInvocation;

public final class MythicSkillAction extends AbstractSkillScriptAction {

    private final MythicSkillCastService mythicSkillCastService;

    public MythicSkillAction(MythicSkillCastService mythicSkillCastService) {
        super("mythic", "bridge", "Cast MythicMobs skill.",
                SkillActionParameter.required("skill", SkillActionParameterType.STRING, "Mythic skill id"));
        this.mythicSkillCastService = mythicSkillCastService;
    }

    @Override
    public CompletableFuture<SkillActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        String skill = arg(arguments, "skill", "");
        TriggerInvocation invocation = context.invocation() instanceof TriggerInvocation triggerInvocation ? triggerInvocation : null;
        if (mythicSkillCastService == null || !mythicSkillCastService.cast(context.caster(), skill, invocation)) {
            return completed(SkillActionResult.failure(SkillActionErrorType.EXECUTION_EXCEPTION, "Mythic skill cast failed: " + skill));
        }
        return completed(SkillActionResult.ok());
    }
}
