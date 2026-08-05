package emaki.jiuwu.craft.cooking.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

/**
 * Re-evaluates the target's nutrition thresholds so buffs and penalties catch up.
 *
 * <p>Useful after a batch of nutrition writes that each suppressed threshold evaluation, or after the
 * thresholds themselves were reconfigured.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: {@code recheckThresholds} takes one player and evaluates only that
 * player's single and combination thresholds. The service does walk the online-player list elsewhere, but that
 * is its {@code reload} path, not this one.</p>
 */
public final class NutritionThresholdRecheckStage implements CoreActionStage {

    private final EmakiCookingPlugin plugin;

    /**
     * Creates the stage.
     *
     * @param plugin owning plugin, source of the nutrition service
     */
    public NutritionThresholdRecheckStage(@NotNull EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String id() {
        return "cooking_recheck_nutrition_threshold";
    }

    @Override
    public @NotNull String description() {
        return "Re-evaluates the target's nutrition thresholds.";
    }

    @Override
    public @NotNull String category() {
        return "cooking";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return List.of();
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.REQUIRED_ENTITY;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (plugin.nutritionService() == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.stage.cooking.service_unavailable");
        }
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        // False means the nutrition system is disabled or the player has no cached record yet. Neither is a
        // fault, so it reports as skipped rather than failed.
        return plugin.nutritionService().recheckThresholds(target)
                ? CoreActionOutcome.success(Map.of("player", target.getName()))
                : CoreActionOutcome.skipped("action.stage.cooking.recheck_unavailable");
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
