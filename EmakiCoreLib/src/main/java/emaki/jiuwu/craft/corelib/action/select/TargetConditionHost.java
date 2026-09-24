package emaki.jiuwu.craft.corelib.action.select;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreTargetCondition;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetConditionArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetOutcome;

@FunctionalInterface
public interface TargetConditionHost {

    TargetConditionHost UNANSWERABLE = (condition, arguments) -> CoreTargetOutcome.UNKNOWN;

    @NotNull
    CoreTargetOutcome test(@NotNull CoreTargetCondition condition,
            @NotNull CoreTargetConditionArguments arguments);
}