package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationParameter;
import emaki.jiuwu.craft.corelib.operation.OperationParameterType;
import emaki.jiuwu.craft.corelib.operation.OperationParsers;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;

public final class DamageOperation extends BaseOperation {

    public DamageOperation() {
        super("damage", "player", "Damage player.", OperationParameter.required("amount", OperationParameterType.DOUBLE, "Amount"));
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        OperationResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        double amount = OperationParsers.parseDouble(arguments.get("amount"), 0D);
        context.player().setHealth(Math.max(0D, context.player().getHealth() - amount));
        return OperationResult.ok();
    }
}
