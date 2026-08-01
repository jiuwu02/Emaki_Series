package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import java.util.Map;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;

/**
 * Removes the target entity.
 *
 * <p>What is left of the v1 {@code killentity} once its radius / limit / type / include_players search moved
 * into the {@code nearby} source. Selecting which entities to affect was never this stage's business; now it
 * takes what the flow gives it and removes it.</p>
 *
 * <p>A player target is refused. {@code Entity#remove} on a player is not a supported operation, and v1's
 * {@code include_players} argument gated a search rather than the removal itself. Killing a player is
 * {@code set_health amount=0}.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: removes one entity, which its owning thread must do.</p>
 */
public final class KillEntityStage extends BaseStage {

    public KillEntityStage() {
        super("kill_entity", "entity", "Removes the target entity.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY);
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Entity target = StageSupport.entity(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_entity");
        }
        if (target instanceof Player) {
            return CoreActionOutcome.skipped("action.v2.stage.kill_entity.player_target");
        }
        if (target.isDead()) {
            return CoreActionOutcome.skipped("action.v2.stage.kill_entity.already_dead");
        }
        target.remove();
        return CoreActionOutcome.success(Map.of("type", target.getType().name().toLowerCase(java.util.Locale.ROOT)));
    }
}
