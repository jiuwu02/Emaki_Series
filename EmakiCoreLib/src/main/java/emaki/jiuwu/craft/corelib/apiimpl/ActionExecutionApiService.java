package emaki.jiuwu.craft.corelib.apiimpl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionEngine;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.PipelineOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.execution.CoreActionCompileDiagnostic;
import emaki.jiuwu.craft.corelib.api.action.execution.CoreActionExecutionContext;
import emaki.jiuwu.craft.corelib.api.action.execution.CoreActionExecutionResult;
import emaki.jiuwu.craft.corelib.api.action.execution.CoreActionStageExecution;
import emaki.jiuwu.craft.corelib.api.action.execution.CoreActionStageExecutionStatus;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.PhaseContract;
import emaki.jiuwu.craft.corelib.api.text.Texts;

final class ActionExecutionApiService {

    private final EmakiCoreLibPlugin plugin;

    ActionExecutionApiService(EmakiCoreLibPlugin plugin) {
        this.plugin = plugin;
    }

    CompletableFuture<CoreActionExecutionResult> execute(Plugin owner,
            String line,
            CoreActionExecutionContext input) {
        CoreActionExecutionContext resolved = input == null
                ? CoreActionExecutionContext.builder().build()
                : input;
        PhaseContract phase = PhaseContract.declared(resolved.phase(), Set.copyOf(resolved.data().keySet()),
                resolved.variables().keySet(), !resolved.targets().isEmpty());
        return execute(owner, line, resolved, phase);
    }

    CompletableFuture<CoreActionExecutionResult> execute(Plugin owner,
            String line,
            CoreActionExecutionContext input,
            PhaseContract phase) {
        if (owner == null) {
            return completed(CoreActionExecutionResult.invalidRequest("action.execution.owner_required"));
        }
        if (!owner.isEnabled()) {
            return completed(CoreActionExecutionResult.executionFailed(CoreActionFailureKind.OWNER_DISABLED,
                    "action.execution.owner_disabled"));
        }
        if (Texts.isBlank(line)) {
            return completed(CoreActionExecutionResult.invalidRequest("action.execution.line_required"));
        }
        ActionEngine engine = plugin.actionEngine();
        if (engine == null) {
            return completed(CoreActionExecutionResult.unavailable("action.execution.engine_unavailable"));
        }

        CoreActionExecutionContext resolved = input == null
                ? CoreActionExecutionContext.builder().build()
                : input;
        ActionLineRunner runner = plugin.actionLineRunner(owner);
        PipelineContext context = runner.context(resolved);
        ActionEngine.Result compiled;
        try {
            compiled = engine.compile(line, phase);
        } catch (RuntimeException | LinkageError exception) {
            return completed(internalFailure("action.execution.compile_exception", exception));
        }
        if (!compiled.successful()) {
            return completed(CoreActionExecutionResult.compileFailed("action.execution.compile_failed", Map.of(),
                    compiled.diagnostics().stream().map(ActionExecutionApiService::diagnostic).toList()));
        }
        return engine.run(owner, compiled.pipeline(), context)
                .thenApply(ActionExecutionApiService::result)
                .exceptionally(throwable -> internalFailure("action.execution.exception", unwrap(throwable)));
    }

    private static CompletableFuture<CoreActionExecutionResult> completed(CoreActionExecutionResult result) {
        return CompletableFuture.completedFuture(result);
    }

    private static CoreActionExecutionResult result(PipelineOutcome outcome) {
        if (outcome == null) {
            return CoreActionExecutionResult.executionFailed(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.execution.no_outcome");
        }
        List<CoreActionStageExecution> stages = outcome.stageResults().stream()
                .map(ActionExecutionApiService::stage)
                .toList();
        return switch (outcome.status()) {
            case SUCCESS -> CoreActionExecutionResult.success(stages, outcome.keptFlow());
            case SKIPPED -> CoreActionExecutionResult.skipped(outcome.reasonKey(), stages, outcome.keptFlow());
            case PARTIAL -> CoreActionExecutionResult.partial(outcome.reasonKey(), stages, outcome.keptFlow());
            case FAILURE -> CoreActionExecutionResult.executionFailed(outcome.failureKind(), outcome.reasonKey(),
                    outcome.args(), stages, outcome.keptFlow());
        };
    }

    private static CoreActionCompileDiagnostic diagnostic(CompileDiagnostic diagnostic) {
        return new CoreActionCompileDiagnostic(diagnostic.reasonKey(), diagnostic.file(), diagnostic.keyPath(),
                diagnostic.line(), diagnostic.column(), diagnostic.token(), diagnostic.detail(),
                diagnostic.candidates());
    }

    private static CoreActionStageExecution stage(PipelineOutcome.StageResult result) {
        CoreActionStageExecutionStatus status = switch (result.status()) {
            case SUCCESS -> CoreActionStageExecutionStatus.SUCCESS;
            case SKIPPED -> CoreActionStageExecutionStatus.SKIPPED;
            case PARTIAL -> CoreActionStageExecutionStatus.PARTIAL;
            case FAILURE -> CoreActionStageExecutionStatus.FAILURE;
        };
        return new CoreActionStageExecution(result.stageId(), status, result.reasonKey(), result.targetCount());
    }

    private static CoreActionExecutionResult internalFailure(String reasonKey, Throwable throwable) {
        return CoreActionExecutionResult.executionFailed(CoreActionFailureKind.INTERNAL_ERROR, reasonKey,
                Map.of("error", throwable == null ? "" : Texts.toStringSafe(throwable.getMessage())),
                List.of(), List.of());
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
