package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

public final class AddVelocityStage extends BaseStage {

    public AddVelocityStage() {
        super("add_velocity", "movement", "给实体叠加速度向量（击退/弹射）。",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("x", CoreStageParameterType.DOUBLE, "X 速度增量"),
                CoreStageParameter.required("y", CoreStageParameterType.DOUBLE, "Y 速度增量"),
                CoreStageParameter.required("z", CoreStageParameterType.DOUBLE, "Z 速度增量"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Entity target = StageSupport.entity(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_entity");
        }
        target.setVelocity(target.getVelocity().add(new Vector(
                arguments.getDouble("x", 0D),
                arguments.getDouble("y", 0D),
                arguments.getDouble("z", 0D))));
        return CoreActionOutcome.success();
    }
}
