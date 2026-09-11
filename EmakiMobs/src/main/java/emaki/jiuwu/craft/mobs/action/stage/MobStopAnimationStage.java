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

public final class MobStopAnimationStage extends BaseStage {

    private final MobIdentifier mobIdentifier;
    private final MobModelManager modelManager;

    public MobStopAnimationStage(MobIdentifier mobIdentifier, MobModelManager modelManager) {
        super("mob_stop_animation", "emakimobs", "Stops a named animation on the target mob's model.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("animation", CoreStageParameterType.STRING, "Animation key"));
        this.mobIdentifier = mobIdentifier;
        this.modelManager = modelManager;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        String animation = arguments.getString("animation");
        if (animation == null || animation.isBlank()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.mob_stop_animation.missing_animation", Map.of());
        }
        CoreActionSubject subject = context.currentTarget();
        Entity raw = subject.entityOrNull();
        if (!(raw instanceof LivingEntity entity)) {
            return CoreActionOutcome.skipped("action.stage.common.no_entity");
        }
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) {
            return CoreActionOutcome.skipped("action.stage.mob_stop_animation.not_managed");
        }
        modelManager.stopNamed(entity, mobId, animation);
        return CoreActionOutcome.success(Map.of("animation", animation));
    }
}
