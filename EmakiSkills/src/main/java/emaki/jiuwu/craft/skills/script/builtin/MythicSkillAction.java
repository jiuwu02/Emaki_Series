package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;
import emaki.jiuwu.craft.skills.mythic.MythicSkillCastService;
import emaki.jiuwu.craft.skills.trigger.TriggerInvocation;

public final class MythicSkillAction extends AbstractSkillScriptAction {

    private final MythicSkillCastService mythicSkillCastService;

    public MythicSkillAction(MythicSkillCastService mythicSkillCastService) {
        super("mythic", "bridge", "Cast MythicMobs skill.",
                ActionParameter.required("skill", ActionParameterType.STRING, "Mythic skill id"));
        this.mythicSkillCastService = mythicSkillCastService;
    }

    @Override
    public CompletableFuture<ActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        String skill = arg(arguments, "skill", "");
        TriggerInvocation invocation = context.invocation() instanceof TriggerInvocation triggerInvocation ? triggerInvocation : null;
        if (mythicSkillCastService == null || !mythicSkillCastService.cast(context.caster(), skill, invocation)) {
            return completed(ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Mythic skill cast failed: " + skill));
        }
        return completed(ActionResult.ok());
    }
}
