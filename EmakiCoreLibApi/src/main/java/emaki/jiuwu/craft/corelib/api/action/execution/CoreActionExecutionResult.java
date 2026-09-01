package emaki.jiuwu.craft.corelib.api.action.execution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;

/**
 * Immutable result returned for one action execution request.
 *
 * @param status overall execution status
 * @param failureKind coarse runtime failure classification, or {@code null} when not applicable
 * @param reasonKey language key describing the overall result
 * @param reasonArguments structured arguments for the overall reason
 * @param diagnostics compile diagnostics
 * @param stages ordered stage summaries
 * @param keptTargets targets retained by the execution
 */
public record CoreActionExecutionResult(@NotNull CoreActionExecutionStatus status,
        @Nullable CoreActionFailureKind failureKind,
        @NotNull String reasonKey,
        @NotNull Map<String, Object> reasonArguments,
        @NotNull List<CoreActionCompileDiagnostic> diagnostics,
        @NotNull List<CoreActionStageExecution> stages,
        @NotNull List<CoreActionSubject> keptTargets) {

    public CoreActionExecutionResult {
        status = status == null ? CoreActionExecutionStatus.EXECUTION_FAILED : status;
        if (status == CoreActionExecutionStatus.EXECUTION_FAILED && failureKind == null) {
            failureKind = CoreActionFailureKind.INTERNAL_ERROR;
        }
        reasonKey = reasonKey == null ? "" : reasonKey;
        reasonArguments = reasonArguments == null ? Map.of() : Map.copyOf(reasonArguments);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        stages = stages == null ? List.of() : List.copyOf(stages);
        keptTargets = copySubjects(keptTargets);
    }

    /** {@return an unavailable result with the given language key} */
    public static @NotNull CoreActionExecutionResult unavailable(@Nullable String reasonKey) {
        return unavailable(reasonKey, Map.of());
    }

    /** {@return an unavailable result with the given language key and arguments} */
    public static @NotNull CoreActionExecutionResult unavailable(@Nullable String reasonKey,
            @Nullable Map<String, Object> reasonArguments) {
        return result(CoreActionExecutionStatus.UNAVAILABLE, null, reasonKey, reasonArguments,
                List.of(), List.of(), List.of());
    }

    /** {@return an invalid-request result with the given language key} */
    public static @NotNull CoreActionExecutionResult invalidRequest(@Nullable String reasonKey) {
        return invalidRequest(reasonKey, Map.of());
    }

    /** {@return an invalid-request result with the given language key and arguments} */
    public static @NotNull CoreActionExecutionResult invalidRequest(@Nullable String reasonKey,
            @Nullable Map<String, Object> reasonArguments) {
        return result(CoreActionExecutionStatus.INVALID_REQUEST, null, reasonKey, reasonArguments,
                List.of(), List.of(), List.of());
    }

    /** {@return a compile-failed result carrying the given diagnostics} */
    public static @NotNull CoreActionExecutionResult compileFailed(
            @Nullable List<CoreActionCompileDiagnostic> diagnostics) {
        return compileFailed("", Map.of(), diagnostics);
    }

    /** {@return a compile-failed result with an overall reason and diagnostics} */
    public static @NotNull CoreActionExecutionResult compileFailed(@Nullable String reasonKey,
            @Nullable Map<String, Object> reasonArguments,
            @Nullable List<CoreActionCompileDiagnostic> diagnostics) {
        return result(CoreActionExecutionStatus.COMPILE_FAILED, CoreActionFailureKind.INVALID_CONFIG,
                reasonKey, reasonArguments, diagnostics, List.of(), List.of());
    }

    /** {@return a successful result with no stage summaries or kept targets} */
    public static @NotNull CoreActionExecutionResult success() {
        return success(List.of(), List.of());
    }

    /** {@return a successful result carrying stage summaries and kept targets} */
    public static @NotNull CoreActionExecutionResult success(
            @Nullable List<CoreActionStageExecution> stages,
            @Nullable List<CoreActionSubject> keptTargets) {
        return result(CoreActionExecutionStatus.SUCCESS, null, "", Map.of(),
                List.of(), stages, keptTargets);
    }

    /** {@return a skipped result with the given language key} */
    public static @NotNull CoreActionExecutionResult skipped(@Nullable String reasonKey) {
        return skipped(reasonKey, List.of(), List.of());
    }

    /** {@return a skipped result carrying stage summaries and kept targets} */
    public static @NotNull CoreActionExecutionResult skipped(@Nullable String reasonKey,
            @Nullable List<CoreActionStageExecution> stages,
            @Nullable List<CoreActionSubject> keptTargets) {
        return result(CoreActionExecutionStatus.SKIPPED, null, reasonKey, Map.of(),
                List.of(), stages, keptTargets);
    }

    /** {@return a partial result carrying stage summaries and kept targets} */
    public static @NotNull CoreActionExecutionResult partial(@Nullable String reasonKey,
            @Nullable List<CoreActionStageExecution> stages,
            @Nullable List<CoreActionSubject> keptTargets) {
        return result(CoreActionExecutionStatus.PARTIAL, null, reasonKey, Map.of(),
                List.of(), stages, keptTargets);
    }

    /** {@return an execution-failed result with the given failure classification and reason} */
    public static @NotNull CoreActionExecutionResult executionFailed(@Nullable CoreActionFailureKind failureKind,
            @Nullable String reasonKey) {
        return executionFailed(failureKind, reasonKey, Map.of(), List.of(), List.of());
    }

    /** {@return an execution-failed result carrying arguments, stage summaries, and kept targets} */
    public static @NotNull CoreActionExecutionResult executionFailed(@Nullable CoreActionFailureKind failureKind,
            @Nullable String reasonKey,
            @Nullable Map<String, Object> reasonArguments,
            @Nullable List<CoreActionStageExecution> stages,
            @Nullable List<CoreActionSubject> keptTargets) {
        CoreActionFailureKind kind = failureKind == null ? CoreActionFailureKind.INTERNAL_ERROR : failureKind;
        return result(CoreActionExecutionStatus.EXECUTION_FAILED, kind, reasonKey, reasonArguments,
                List.of(), stages, keptTargets);
    }

    @Override
    public @NotNull List<CoreActionSubject> keptTargets() {
        return copySubjects(keptTargets);
    }

    private static CoreActionExecutionResult result(CoreActionExecutionStatus status,
            CoreActionFailureKind failureKind,
            String reasonKey,
            Map<String, Object> reasonArguments,
            List<CoreActionCompileDiagnostic> diagnostics,
            List<CoreActionStageExecution> stages,
            List<CoreActionSubject> keptTargets) {
        return new CoreActionExecutionResult(status, failureKind, reasonKey, reasonArguments,
                diagnostics, stages, keptTargets);
    }

    private static List<CoreActionSubject> copySubjects(Collection<? extends CoreActionSubject> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<CoreActionSubject> copy = new ArrayList<>(source.size());
        for (CoreActionSubject subject : source) {
            copy.add(copySubject(subject));
        }
        return List.copyOf(copy);
    }

    private static CoreActionSubject copySubject(CoreActionSubject subject) {
        if (subject == null) {
            return CoreActionSubject.absent();
        }
        if (subject instanceof CoreActionSubject.OfLocation located) {
            return new CoreActionSubject.OfLocation(located.location());
        }
        return subject;
    }
}
