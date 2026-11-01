package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

public final class SetAiStage extends BaseStage {

    public SetAiStage() {
        super("set_ai", "meta", "控制生物的 AI 开关。",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("enabled", CoreStageParameterType.BOOLEAN, "是否启用 AI"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (!(context.currentTarget().entityOrNull() instanceof LivingEntity living)) {
            return CoreActionOutcome.skipped("action.stage.common.not_entity");
        }
        living.setAI(arguments.getBoolean("enabled", true));
        return CoreActionOutcome.success();
    }
}
