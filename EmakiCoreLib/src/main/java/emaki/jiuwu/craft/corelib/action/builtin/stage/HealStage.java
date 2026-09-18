package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
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

public final class HealStage extends BaseStage {

    private final ActionAuditLogger auditLogger;

    public HealStage(ActionAuditLogger auditLogger) {
        super("heal", "entity", "Restores health on the target.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("amount", CoreStageParameterType.DOUBLE, "Health to restore"));
        this.auditLogger = auditLogger;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        LivingEntity target = StageSupport.livingEntity(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_living_entity");
        }
        double amount = arguments.getDouble("amount", 0D);
        if (!Double.isFinite(amount) || amount < 0D) {
            Map<String, Object> args = Map.of("amount", amount);
            auditLogger.logFailure(id(), target, OperationType.INCREASE, amount,
                    "action.stage.common.invalid_amount", args, context);
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.common.invalid_amount", args);
        }
        AttributeInstance attribute = target.getAttribute(Attribute.MAX_HEALTH);
        double max = attribute == null ? target.getHealth() : attribute.getValue();
        double before = target.getHealth();
        double after = Math.min(max, before + amount);
        target.setHealth(Math.max(0D, after));
        auditLogger.logSuccess(id(), target, OperationType.INCREASE, before, target.getHealth(), amount, context);
        return CoreActionOutcome.success(Map.of("health_before", before, "health_after", target.getHealth()));
    }
}