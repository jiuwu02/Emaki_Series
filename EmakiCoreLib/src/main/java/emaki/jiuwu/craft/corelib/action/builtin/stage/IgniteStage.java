package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import java.util.Map;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;

/**
 * Sets the target on fire for a duration.
 *
 * <p>Works on any entity, since {@code Entity#setFireTicks} is defined at that level.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: writes one entity's fire ticks.</p>
 */
public final class IgniteStage extends BaseStage {

    public IgniteStage() {
        super("ignite", "entity", "Sets the target on fire for a duration.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.optional("duration", CoreStageParameterType.DURATION, "5s",
                        "Fire duration"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Entity target = StageSupport.entity(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_entity");
        }
        long ticks = Math.max(0L, arguments.getDurationTicks("duration", 100L));
        target.setFireTicks((int) Math.min(Integer.MAX_VALUE, ticks));
        return CoreActionOutcome.success(Map.of("fire_ticks", target.getFireTicks()));
    }
}
