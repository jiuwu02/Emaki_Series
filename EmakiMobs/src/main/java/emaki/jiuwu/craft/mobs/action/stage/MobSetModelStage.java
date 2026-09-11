package emaki.jiuwu.craft.mobs.action.stage;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.mobs.model.MobModelManager;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class MobSetModelStage extends BaseStage {

    private final MobIdentifier mobIdentifier;
    private final MobModelManager modelManager;

    public MobSetModelStage(MobIdentifier mobIdentifier, MobModelManager modelManager) {
        super("mob_set_model", "emakimobs", "Swaps the model blueprint attached to the target mob.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("blueprint", CoreStageParameterType.STRING, "Model blueprint id"));
        this.mobIdentifier = mobIdentifier;
        this.modelManager = modelManager;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        String blueprint = arguments.getString("blueprint");
        if (blueprint == null || blueprint.isBlank()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.mob_set_model.missing_blueprint", Map.of());
        }
        CoreActionSubject subject = context.currentTarget();
        Entity raw = subject.entityOrNull();
        if (!(raw instanceof LivingEntity entity)) {
            return CoreActionOutcome.skipped("action.stage.common.no_entity");
        }
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) {
            return CoreActionOutcome.skipped("action.stage.mob_set_model.not_managed");
        }
        modelManager.setModel(entity, mobId, blueprint.trim());
        return CoreActionOutcome.success(Map.of("blueprint", blueprint));
    }
}
