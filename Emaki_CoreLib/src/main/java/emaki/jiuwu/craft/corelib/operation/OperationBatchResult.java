package emaki.jiuwu.craft.corelib.operation;

import java.util.List;

public record OperationBatchResult(boolean success, List<OperationStepResult> steps) {

    public OperationStepResult firstFailure() {
        for (OperationStepResult step : steps) {
            if (step != null && step.result() != null && !step.result().success()) {
                return step;
            }
        }
        return null;
    }
}
