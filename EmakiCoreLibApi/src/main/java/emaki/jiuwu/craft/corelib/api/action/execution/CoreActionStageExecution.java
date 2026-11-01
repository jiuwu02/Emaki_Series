package emaki.jiuwu.craft.corelib.api.action.execution;

import org.jetbrains.annotations.NotNull;

/**
 * Summary of one stage within an action execution.
 *
 * @param stageId stable stage identifier
 * @param status stage outcome
 * @param reasonKey language key describing a skip, partial result, or failure
 * @param targetCount number of targets represented by this summary
 */
public record CoreActionStageExecution(@NotNull String stageId,
        @NotNull CoreActionStageExecutionStatus status,
        @NotNull String reasonKey,
        int targetCount) {

    public CoreActionStageExecution {
        stageId = stageId == null ? "" : stageId.trim();
        status = status == null ? CoreActionStageExecutionStatus.FAILURE : status;
        reasonKey = reasonKey == null ? "" : reasonKey;
        targetCount = Math.max(0, targetCount);
    }
}
