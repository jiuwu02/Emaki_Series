package emaki.jiuwu.craft.corelib.action.v2.exec;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;

/**
 * Result of running one pipeline.
 *
 * @param status overall status
 * @param failureKind failure classification, {@code null} unless {@code status} is
 *        {@link Status#FAILURE}
 * @param reasonKey language key describing the outcome
 * @param args diagnostic arguments for {@code reasonKey}
 * @param stageResults per-stage summary in execution order
 */
public record PipelineOutcome(@NotNull Status status,
        @Nullable CoreActionFailureKind failureKind,
        @NotNull String reasonKey,
        @NotNull Map<String, Object> args,
        @NotNull List<StageResult> stageResults) {

    public PipelineOutcome {
        status = status == null ? Status.SUCCESS : status;
        reasonKey = reasonKey == null ? "" : reasonKey;
        args = args == null ? Map.of() : Map.copyOf(args);
        stageResults = stageResults == null ? List.of() : List.copyOf(stageResults);
    }

    /** Overall pipeline status. */
    public enum Status {

        /** Every executed stage succeeded. */
        SUCCESS,

        /** The pipeline stopped early for a normal reason, such as an empty flow or a lost chance roll. */
        SKIPPED,

        /** Some targets succeeded and others failed. */
        PARTIAL,

        /** The pipeline could not complete. */
        FAILURE
    }

    /**
     * One stage's contribution.
     *
     * @param stageId stage name
     * @param status how the stage ended
     * @param reasonKey language key describing it
     * @param targetCount how many targets it saw
     */
    public record StageResult(@NotNull String stageId,
            @NotNull Status status,
            @NotNull String reasonKey,
            int targetCount) {

        public StageResult {
            stageId = stageId == null ? "" : stageId;
            status = status == null ? Status.SUCCESS : status;
            reasonKey = reasonKey == null ? "" : reasonKey;
            targetCount = Math.max(0, targetCount);
        }
    }

    /** {@return whether nothing failed} */
    public boolean successful() {
        return status == Status.SUCCESS;
    }

    /**
     * Creates a success outcome.
     *
     * @param stageResults per-stage summary
     * @return the outcome
     */
    public static @NotNull PipelineOutcome success(@Nullable List<StageResult> stageResults) {
        return new PipelineOutcome(Status.SUCCESS, null, "", Map.of(), stageResults);
    }

    /**
     * Creates a skipped outcome.
     *
     * @param reasonKey language key
     * @param stageResults per-stage summary
     * @return the outcome
     */
    public static @NotNull PipelineOutcome skipped(@Nullable String reasonKey,
            @Nullable List<StageResult> stageResults) {
        return new PipelineOutcome(Status.SKIPPED, null, reasonKey, Map.of(), stageResults);
    }

    /**
     * Creates a partial outcome.
     *
     * @param reasonKey language key
     * @param args diagnostic arguments
     * @param stageResults per-stage summary
     * @return the outcome
     */
    public static @NotNull PipelineOutcome partial(@Nullable String reasonKey,
            @Nullable Map<String, Object> args,
            @Nullable List<StageResult> stageResults) {
        return new PipelineOutcome(Status.PARTIAL, null, reasonKey, args, stageResults);
    }

    /**
     * Creates a failure outcome.
     *
     * @param failureKind failure classification
     * @param reasonKey language key
     * @param args diagnostic arguments
     * @param stageResults per-stage summary
     * @return the outcome
     */
    public static @NotNull PipelineOutcome failure(@Nullable CoreActionFailureKind failureKind,
            @Nullable String reasonKey,
            @Nullable Map<String, Object> args,
            @Nullable List<StageResult> stageResults) {
        return new PipelineOutcome(Status.FAILURE,
                failureKind == null ? CoreActionFailureKind.INTERNAL_ERROR : failureKind,
                reasonKey, args, stageResults);
    }
}
