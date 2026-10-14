package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
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

public final class SetRotationStage extends BaseStage {

    public SetRotationStage() {
        super("set_rotation", "movement", "设置实体朝向（yaw/pitch）。",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("yaw", CoreStageParameterType.DOUBLE, "水平朝向角度"),
                CoreStageParameter.required("pitch", CoreStageParameterType.DOUBLE, "俯仰角度"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Entity target = StageSupport.entity(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_entity");
        }
        float yaw = (float) arguments.getDouble("yaw", 0D);
        float pitch = (float) arguments.getDouble("pitch", 0D);
        Location location = target.getLocation();
        location.setYaw(yaw);
        location.setPitch(pitch);
        target.teleport(location);
        return CoreActionOutcome.success();
    }
}
