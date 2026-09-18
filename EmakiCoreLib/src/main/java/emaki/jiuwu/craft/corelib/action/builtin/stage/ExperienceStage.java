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
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger.OperationType;

abstract class ExperienceStage extends BaseStage {

    private final ActionAuditLogger auditLogger;
    private final OperationType operation;
    private final boolean requirePositive;

    ExperienceStage(String id,
            String description,
            ActionAuditLogger auditLogger,
            OperationType operation) {
        super(id, "player", description,
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("amount", CoreStageParameterType.INTEGER, "Amount"),
                CoreStageParameter.optional("mode", CoreStageParameterType.STRING, "points",
                        "points or levels"));
        this.auditLogger = auditLogger;
        this.operation = operation;
        this.requirePositive = operation == OperationType.DECREASE;
    }

    @Override
    public final @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        int amount = arguments.getInt("amount", 0);
        if (requirePositive ? amount <= 0 : amount < 0) {
            String reasonKey = requirePositive
                    ? "action.stage.common.invalid_positive_amount"
                    : "action.stage.common.invalid_amount";
            Map<String, Object> args = Map.of("amount", amount);
            auditLogger.logFailure(id(), target, operation, amount, reasonKey, args, context);
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG, reasonKey, args);
        }
        boolean levels = "levels".equalsIgnoreCase(arguments.getString("mode", "points"));
        int before = levels ? target.getLevel() : target.getTotalExperience();
        CoreActionOutcome outcome = apply(target, amount, levels);
        if (outcome instanceof CoreActionOutcome.Failure failure) {
            auditLogger.logFailure(id(), target, operation, amount, failure.reasonKey(), failure.args(), context);
            return outcome;
        }
        int after = levels ? target.getLevel() : target.getTotalExperience();
        auditLogger.logSuccess(id(), target, operation, before, after, amount, context);
        return CoreActionOutcome.success(Map.of(
                "level", target.getLevel(),
                "total_experience", target.getTotalExperience()));
    }

    abstract CoreActionOutcome apply(Player target, int amount, boolean levels);

    static void setTotalExperience(Player player, int total) {
        int remaining = Math.max(0, total);
        player.setExp(0F);
        player.setLevel(0);
        player.setTotalExperience(0);
        if (remaining > 0) {
            player.giveExp(remaining);
        }
    }
}