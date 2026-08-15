package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

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
            return CoreActionOutcome.skipped("action.stage.common.not_entity");
        }
        if (target instanceof Player) {
            return CoreActionOutcome.skipped("action.stage.kill_entity.player_target");
        }
        if (target.isDead()) {
            return CoreActionOutcome.skipped("action.stage.kill_entity.already_dead");
        }
        target.remove();
        return CoreActionOutcome.success(Map.of("type", target.getType().name().toLowerCase(Locale.ROOT)));
    }
}
