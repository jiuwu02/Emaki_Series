package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
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
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Shared body for {@code give_money}, {@code take_money} and {@code set_money}.
 *
 * <p>{@link EconomyManager} still speaks the v1 {@code ActionResult}, so the three stages convert its result
 * rather than reaching into economy providers themselves. That conversion is the only place the two result types
 * meet, and it maps the economy-specific error codes onto the pipeline failure kinds: an unavailable provider or
 * unknown currency is {@code INVALID_CONFIG} because the server owner named something that does not exist, while
 * an insufficient balance is {@code REJECTED} because the request was well-formed and the domain refused it.</p>
 *
 * <p>Requires a {@code Player}: every {@code EconomyProvider} method is defined in terms of one.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: economy providers are keyed by player and commonly touch player state.</p>
 */
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

    /**
     * Performs the economy operation.
     *
     * @param economy the economy manager
     * @param target the affected player
     * @param provider provider id, {@code auto} for the configured default
     * @param currency currency id, blank for the provider default
     * @param amount the amount
     * @return the v1 result to convert
     */
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
