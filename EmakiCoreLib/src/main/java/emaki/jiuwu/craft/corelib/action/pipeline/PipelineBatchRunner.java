package emaki.jiuwu.craft.corelib.action.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.PhaseContract;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.PipelineOutcome;

public final class PipelineBatchRunner {

    private final Map<CacheKey, Object> cache = new ConcurrentHashMap<>();

    public @NotNull List<CompiledPipeline> compile(@Nullable ActionEngine engine,
            @Nullable List<String> lines,
            @Nullable PhaseContract phase,
            @Nullable Consumer<CompileDiagnostic> onDiagnostic) {
        if (engine == null || lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<CompiledPipeline> compiled = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            CacheKey key = new CacheKey(System.identityHashCode(engine), line,
                    phase == null ? PhaseContract.permissive("default").cacheKey() : phase.cacheKey());
            Object cached = cache.get(key);
            if (cached instanceof CompiledPipeline hit) {
                compiled.add(hit);
                continue;
            }
            if (cached != null) {

                continue;
            }
            ActionEngine.Result result = engine.compile(line, phase);
            if (result.successful() && result.pipeline() != null) {
                cache.put(key, result.pipeline());
                compiled.add(result.pipeline());
                continue;
            }
            cache.put(key, Boolean.FALSE);
            if (onDiagnostic != null) {
                result.diagnostics().forEach(onDiagnostic);
            }
        }
        return List.copyOf(compiled);
    }

    public @NotNull CompletableFuture<Boolean> run(@NotNull Plugin owner,
            @Nullable ActionEngine engine,
            @Nullable List<CompiledPipeline> body,
            @NotNull PipelineContext context,
            boolean stopOnFailure) {
        if (engine == null || body == null || body.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        return runFrom(owner, engine, body, context, stopOnFailure, 0, true);
    }

    public @NotNull CompletableFuture<Boolean> compileAndRun(@NotNull Plugin owner,
            @Nullable ActionEngine engine,
            @Nullable List<String> lines,
            @NotNull PipelineContext context,
            boolean stopOnFailure,
            @Nullable Consumer<CompileDiagnostic> onDiagnostic) {
        return compileAndRun(owner, engine, lines, context, null, stopOnFailure, onDiagnostic);
    }

    public @NotNull CompletableFuture<Boolean> compileAndRun(@NotNull Plugin owner,
            @Nullable ActionEngine engine,
            @Nullable List<String> lines,
            @NotNull PipelineContext context,
            @Nullable PhaseContract phase,
            boolean stopOnFailure,
            @Nullable Consumer<CompileDiagnostic> onDiagnostic) {
        List<CompiledPipeline> body = compile(engine, lines, phase, onDiagnostic);
        return run(owner, engine, body, context, stopOnFailure);
    }

    private CompletableFuture<Boolean> runFrom(Plugin owner,
            ActionEngine engine,
            List<CompiledPipeline> body,
            PipelineContext context,
            boolean stopOnFailure,
            int index,
            boolean successSoFar) {
        if (index >= body.size()) {
            return CompletableFuture.completedFuture(successSoFar);
        }
        return engine.run(owner, body.get(index), context).thenCompose(outcome -> {
            boolean failed = outcome != null && outcome.status() == PipelineOutcome.Status.FAILURE;
            if (failed && stopOnFailure) {
                return CompletableFuture.completedFuture(false);
            }
            return runFrom(owner, engine, body, context, stopOnFailure, index + 1,
                    successSoFar && !failed);
        }).exceptionallyCompose(throwable -> stopOnFailure
                ? CompletableFuture.completedFuture(false)
                : runFrom(owner, engine, body, context, stopOnFailure, index + 1, false));
    }

    public void invalidate() {
        cache.clear();
    }

    public int cachedCount() {
        return cache.size();
    }

    private record CacheKey(int engineIdentity, @NotNull String line, @NotNull String phase) {
    }
}
