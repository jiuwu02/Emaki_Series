package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;

public final class TakeExpOperation extends AbstractExperienceOperation {

    public TakeExpOperation() {
        super("take_exp", "Take experience.");
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        OperationResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        if (levels(arguments)) {
            context.player().setLevel(Math.max(0, context.player().getLevel() - amount(arguments)));
            return OperationResult.ok();
        }
        ExperienceSupport.setTotalExperience(context.player(), Math.max(0, context.player().getTotalExperience() - amount(arguments)));
        return OperationResult.ok();
    }
}
