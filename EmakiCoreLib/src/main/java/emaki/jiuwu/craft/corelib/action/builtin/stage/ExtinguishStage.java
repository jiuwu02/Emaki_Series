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
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;

/**
 * Puts out the fire on the target.
 *
 * <p>Domain {@code CONTEXT_ENTITY}: writes one entity's fire ticks.</p>
 */
public final class ExtinguishStage extends BaseStage {

    public ExtinguishStage() {
        super("extinguish", "entity", "Puts out the fire on the target.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY);
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Entity target = StageSupport.entity(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_entity");
        }
        int before = target.getFireTicks();
        target.setFireTicks(0);
        return CoreActionOutcome.success(Map.of("fire_ticks_before", before, "fire_ticks_after", 0));
    }
}
