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

public final class SetArrowsInBodyStage extends BaseStage {

    public SetArrowsInBodyStage() {
        super("set_arrows_in_body", "entity", "设置插在实体身上的箭矢数量。",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("count", CoreStageParameterType.INTEGER, "箭矢数量"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (!(context.currentTarget().entityOrNull() instanceof LivingEntity living)) {
            return CoreActionOutcome.skipped("action.stage.common.not_entity");
        }
        living.setArrowsInBody(Math.max(0, arguments.getInt("count", 0)));
        return CoreActionOutcome.success();
    }
}
