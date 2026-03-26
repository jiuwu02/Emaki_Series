package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;

public final class GiveExpOperation extends AbstractExperienceOperation {

    public GiveExpOperation() {
        super("give_exp", "Give experience.");
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        OperationResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        if (levels(arguments)) {
            context.player().giveExpLevels(amount(arguments));
        } else {
            context.player().giveExp(amount(arguments));
        }
        return OperationResult.ok();
    }
}
