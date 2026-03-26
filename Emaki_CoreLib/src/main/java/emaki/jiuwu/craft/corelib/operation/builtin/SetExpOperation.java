package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;

public final class SetExpOperation extends AbstractExperienceOperation {

    public SetExpOperation() {
        super("set_exp", "Set experience.");
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        OperationResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        if (levels(arguments)) {
            context.player().setLevel(amount(arguments));
            return OperationResult.ok();
        }
        ExperienceSupport.setTotalExperience(context.player(), amount(arguments));
        return OperationResult.ok();
    }
}
