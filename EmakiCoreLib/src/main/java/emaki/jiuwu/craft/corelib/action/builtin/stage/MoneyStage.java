package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.api.action.ActionResult;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger.OperationType;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;

abstract class MoneyStage extends BaseStage {

    private final EconomyManager economyManager;
    private final ActionAuditLogger auditLogger;
    private final OperationType operation;
    private final boolean allowZero;

    MoneyStage(String id,
            String description,
            EconomyManager economyManager,
            ActionAuditLogger auditLogger,
            OperationType operation,
            boolean allowZero) {
        super(id, "economy", description,
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("amount", CoreStageParameterType.DOUBLE, "Amount"),
                CoreStageParameter.optional("provider", CoreStageParameterType.STRING, "auto",
                        "Economy provider id"),
                CoreStageParameter.optional("currency", CoreStageParameterType.STRING, "", "Currency id"));
        this.economyManager = economyManager;
        this.auditLogger = auditLogger;
        this.operation = operation;
        this.allowZero = allowZero;
    }

    @Override
    public final @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        String provider = arguments.getString("provider", "auto");
        String currency = arguments.getString("currency");
        double amount = arguments.getDouble("amount", 0D);
        if (!Double.isFinite(amount) || amount < 0D || (!allowZero && amount <= 0D)) {
            CoreActionOutcome failure = CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.money.invalid_amount", Map.of("amount", amount));
            auditLogger.logFailure(id(), target, operation, amount, "action.stage.money.invalid_amount",
                    failure instanceof CoreActionOutcome.Failure result ? result.args() : Map.of(), context);
            return failure;
        }
        if (economyManager == null) {
            CoreActionOutcome failure = CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.stage.money.service_unavailable");
            auditLogger.logFailure(id(), target, operation, amount, "action.stage.money.service_unavailable",
                    Map.of(), context);
            return failure;
        }
        double before = economyManager.getBalance(target, provider, currency);
        CoreActionOutcome outcome = convert(perform(economyManager, target, provider, currency, amount));
        if (outcome instanceof CoreActionOutcome.Failure failure) {
            auditLogger.logFailure(id(), target, operation, amount, failure.reasonKey(), failure.args(), context);
            return outcome;
        }
        double after = economyManager.getBalance(target, provider, currency);
        auditLogger.logSuccess(id(), target, operation, before, after, amount, context);
        return outcome;
    }

    abstract ActionResult perform(EconomyManager economy,
            Player target,
            String provider,
            String currency,
            double amount);

    private static CoreActionOutcome convert(ActionResult result) {
        if (result == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.stage.money.no_result");
        }
        if (result.success()) {
            return CoreActionOutcome.success(result.data());
        }
        ActionErrorType errorType = result.errorType() == null
                ? ActionErrorType.EXECUTION_EXCEPTION
                : result.errorType();
        return CoreActionOutcome.failure(failureKind(errorType), reasonKey(errorType),
                Map.of("error", Texts.toStringSafe(result.errorMessage())));
    }

    private static CoreActionFailureKind failureKind(ActionErrorType errorType) {
        return switch (errorType) {
            case PROVIDER_UNAVAILABLE, CURRENCY_NOT_FOUND, INVALID_ARGUMENT -> CoreActionFailureKind.INVALID_CONFIG;
            case INSUFFICIENT_BALANCE -> CoreActionFailureKind.REJECTED;
            default -> CoreActionFailureKind.INTERNAL_ERROR;
        };
    }

    private static String reasonKey(ActionErrorType errorType) {
        return switch (errorType) {
            case PROVIDER_UNAVAILABLE -> "action.stage.money.provider_unavailable";
            case CURRENCY_NOT_FOUND -> "action.stage.money.currency_not_found";
            case INSUFFICIENT_BALANCE -> "action.stage.money.insufficient_balance";
            case INVALID_ARGUMENT -> "action.stage.money.invalid_argument";
            default -> "action.stage.money.failed";
        };
    }
}
