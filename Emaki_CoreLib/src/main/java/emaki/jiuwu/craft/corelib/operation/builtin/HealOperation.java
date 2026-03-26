package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationParameter;
import emaki.jiuwu.craft.corelib.operation.OperationParameterType;
import emaki.jiuwu.craft.corelib.operation.OperationParsers;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;

public final class HealOperation extends BaseOperation {

    public HealOperation() {
        super("heal", "player", "Heal player.", OperationParameter.required("amount", OperationParameterType.DOUBLE, "Amount"));
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        OperationResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        double amount = OperationParsers.parseDouble(arguments.get("amount"), 0D);
        AttributeInstance attribute = context.player().getAttribute(Attribute.MAX_HEALTH);
        double max = attribute == null ? context.player().getHealth() : attribute.getValue();
        context.player().setHealth(Math.min(max, context.player().getHealth() + amount));
        return OperationResult.ok();
    }
}
