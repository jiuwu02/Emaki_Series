package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.api.action.ActionResult;
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
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.api.text.Texts;

abstract class MoneyStage extends BaseStage {

    private final EconomyManager economyManager;

    MoneyStage(String id, String description, EconomyManager economyManager) {
        super(id, "economy", description,
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("amount", CoreStageParameterType.DOUBLE, "Amount"),
                CoreStageParameter.optional("provider", CoreStageParameterType.STRING, "auto",
                        "Economy provider id"),
                CoreStageParameter.optional("currency", CoreStageParameterType.STRING, "", "Currency id"));
        this.economyManager = economyManager;
    }

    @Override
    public final @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        if (economyManager == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.stage.money.service_unavailable");
        }
        return convert(perform(economyManager, target,
                arguments.getString("provider", "auto"),
                arguments.getString("currency"),
                arguments.getDouble("amount", 0D)));
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
            case PROVIDER_UNAVAILABLE, CURRENCY_NOT_FOUND -> CoreActionFailureKind.INVALID_CONFIG;
            case INSUFFICIENT_BALANCE -> CoreActionFailureKind.REJECTED;
            case INVALID_ARGUMENT -> CoreActionFailureKind.INVALID_CONFIG;
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
