package emaki.jiuwu.craft.corelib.action;

import java.util.List;

public record ActionBatchResult(boolean success, List<ActionStepResult> steps) {

    public ActionStepResult firstFailure() {
        for (ActionStepResult step : steps) {
            if (step != null && step.result() != null && !step.result().success()) {
                return step;
            }
        }
        return null;
    }
}
