package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationParameter;
import emaki.jiuwu.craft.corelib.operation.OperationParameterType;
import emaki.jiuwu.craft.corelib.operation.OperationParsers;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;

abstract class AbstractMoneyOperation extends BaseOperation {

    protected final EconomyManager economyManager;

    AbstractMoneyOperation(String id, EconomyManager economyManager, String description) {
        super(
            id,
            "economy",
            description,
            OperationParameter.required("amount", OperationParameterType.DOUBLE, "Amount"),
            OperationParameter.optional("provider", OperationParameterType.STRING, "auto", "Provider"),
            OperationParameter.optional("currency", OperationParameterType.STRING, "", "Currency")
        );
        this.economyManager = economyManager;
    }

    protected double amount(Map<String, String> arguments) {
        return OperationParsers.parseDouble(arguments.get("amount"), 0D);
    }

    protected String provider(Map<String, String> arguments) {
        return stringArg(arguments, "provider");
    }

    protected String currency(Map<String, String> arguments) {
        return stringArg(arguments, "currency");
    }

    @Override
    public final OperationResult execute(OperationContext context, Map<String, String> arguments) {
        OperationResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        return perform(context, arguments);
    }

    protected abstract OperationResult perform(OperationContext context, Map<String, String> arguments);
}
