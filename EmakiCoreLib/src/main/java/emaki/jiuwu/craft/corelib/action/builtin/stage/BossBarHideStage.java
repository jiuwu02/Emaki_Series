package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Hides one of the target's boss bars, or all of them with {@code id=all}.
 *
 * <p>Domain {@code CONTEXT_ENTITY}: detaches a bar from one player's connection.</p>
 */
public final class BossBarHideStage extends BaseStage {

    public BossBarHideStage() {
        super("boss_bar_hide", "feedback", "Hides a per-player boss bar by id.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("id", CoreStageParameterType.STRING, "Boss bar id, or all"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        String id = Texts.trim(arguments.getString("id"));
        if (id.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.boss_bar.id_required");
        }
        if ("all".equalsIgnoreCase(id) || "*".equals(id)) {
            int removed = BossBarStore.hideAll(target);
            return removed <= 0
                    ? CoreActionOutcome.skipped("action.stage.boss_bar.none_active")
                    : CoreActionOutcome.success(Map.of("removed", removed));
        }
        return BossBarStore.hide(target, id)
                ? CoreActionOutcome.success(Map.of("id", Texts.normalizeId(id)))
                : CoreActionOutcome.skipped("action.stage.boss_bar.not_found");
    }
}
