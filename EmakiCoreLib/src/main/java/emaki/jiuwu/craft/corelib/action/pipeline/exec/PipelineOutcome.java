package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;

public record PipelineOutcome(@NotNull Status status,
        @Nullable CoreActionFailureKind failureKind,
        @NotNull String reasonKey,
        @NotNull Map<String, Object> args,
        @NotNull List<StageResult> stageResults,
        @NotNull List<CoreActionSubject> keptFlow) {

    public PipelineOutcome {
        status = status == null ? Status.SUCCESS : status;
        reasonKey = reasonKey == null ? "" : reasonKey;
        args = args == null ? Map.of() : Map.copyOf(args);
        stageResults = stageResults == null ? List.of() : List.copyOf(stageResults);
        keptFlow = keptFlow == null ? List.of() : List.copyOf(keptFlow);
    }

    public enum Status {

        SUCCESS,

        SKIPPED,

        PARTIAL,

        FAILURE
    }

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

    public boolean successful() {
        return status == Status.SUCCESS;
    }

    public static @NotNull PipelineOutcome success(@Nullable List<StageResult> stageResults) {
        return success(stageResults, List.of());
    }

    public static @NotNull PipelineOutcome success(@Nullable List<StageResult> stageResults,
            @Nullable List<CoreActionSubject> keptFlow) {
        return new PipelineOutcome(Status.SUCCESS, null, "", Map.of(), stageResults, keptFlow);
    }

    public static @NotNull PipelineOutcome skipped(@Nullable String reasonKey,
            @Nullable List<StageResult> stageResults) {
        return skipped(reasonKey, stageResults, List.of());
    }

    public static @NotNull PipelineOutcome skipped(@Nullable String reasonKey,
            @Nullable List<StageResult> stageResults,
            @Nullable List<CoreActionSubject> keptFlow) {
        return new PipelineOutcome(Status.SKIPPED, null, reasonKey, Map.of(), stageResults, keptFlow);
    }

    public static @NotNull PipelineOutcome partial(@Nullable String reasonKey,
            @Nullable Map<String, Object> args,
            @Nullable List<StageResult> stageResults) {
        return partial(reasonKey, args, stageResults, List.of());
    }

    public static @NotNull PipelineOutcome partial(@Nullable String reasonKey,
            @Nullable Map<String, Object> args,
            @Nullable List<StageResult> stageResults,
            @Nullable List<CoreActionSubject> keptFlow) {
        return new PipelineOutcome(Status.PARTIAL, null, reasonKey, args, stageResults, keptFlow);
    }

    public static @NotNull PipelineOutcome failure(@Nullable CoreActionFailureKind failureKind,
            @Nullable String reasonKey,
            @Nullable Map<String, Object> args,
            @Nullable List<StageResult> stageResults) {
        return failure(failureKind, reasonKey, args, stageResults, List.of());
    }

    public static @NotNull PipelineOutcome failure(@Nullable CoreActionFailureKind failureKind,
            @Nullable String reasonKey,
            @Nullable Map<String, Object> args,
            @Nullable List<StageResult> stageResults,
            @Nullable List<CoreActionSubject> keptFlow) {
        return new PipelineOutcome(Status.FAILURE,
                failureKind == null ? CoreActionFailureKind.INTERNAL_ERROR : failureKind,
                reasonKey, args, stageResults, keptFlow);
    }
}
