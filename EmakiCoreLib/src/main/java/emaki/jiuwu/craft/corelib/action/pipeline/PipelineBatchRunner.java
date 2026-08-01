package emaki.jiuwu.craft.corelib.action.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.v2.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.action.v2.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.action.v2.compile.PhaseContract;
import emaki.jiuwu.craft.corelib.action.v2.exec.PipelineOutcome;

/**
 * Runs a list of configured pipeline lines in order.
 *
 * <p>Almost every trigger in this project is "here is a list of lines from config, run them for this
 * player": recipe rewards, level-up actions, item conditions, advancement grants. {@link ActionEngine}
 * only compiles and runs one line at a time, so without this each module would reimplement the same
 * sequencing, caching and failure-aggregation logic, as one module already had to.</p>
 *
 * <p>Compiled results are cached per line, keyed by text and engine identity. Compilation walks a lexer,
 * parser and validator, and these lines run on gameplay events, so recompiling per invocation would put
 * that work on the hot path. The engine is rebuilt on every CoreLib reload, and including its identity in
 * the key is what makes a stale entry impossible to hit after the stage table changes.</p>
 */
public final class PipelineBatchRunner {

    private final Map<CacheKey, Object> cache = new ConcurrentHashMap<>();

    /**
     * Compiles one batch of lines.
     *
     * <p>Compiling ahead of running is what allows a configuration error to surface at load time rather
     * than mid-gameplay. Lines that fail are reported and dropped from the batch instead of aborting it,
     * so one broken line does not disable the rest of a reward list.</p>
     *
     * @param engine the live engine
     * @param lines configured pipeline lines
     * @param phase what the triggering phase provides, may be {@code null}
     * @param onDiagnostic receives every compile problem, may be {@code null}
     * @return the compiled lines, in order, excluding the ones that did not compile
     */
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
                    phase == null ? "" : phase.phaseId());
            Object cached = cache.get(key);
            if (cached instanceof CompiledPipeline hit) {
                compiled.add(hit);
                continue;
            }
            if (cached != null) {
                // A previous attempt failed; the diagnostics were already reported then.
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

    /**
     * Runs already compiled lines in order.
     *
     * <p>Sequential rather than parallel: configured lines routinely depend on the ones before them, so
     * running them concurrently would make ordering a race.</p>
     *
     * @param owner plugin owning the invocation
     * @param engine the live engine
     * @param body compiled lines
     * @param context the root context
     * @param stopOnFailure whether the first failure ends the batch
     * @return whether every executed line succeeded
     */
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

    /**
     * Compiles and runs in one call.
     *
     * @param owner plugin owning the invocation
     * @param engine the live engine
     * @param lines configured pipeline lines
     * @param context the root context
     * @param stopOnFailure whether the first failure ends the batch
     * @param onDiagnostic receives every compile problem, may be {@code null}
     * @return whether every executed line succeeded
     */
    public @NotNull CompletableFuture<Boolean> compileAndRun(@NotNull Plugin owner,
            @Nullable ActionEngine engine,
            @Nullable List<String> lines,
            @NotNull PipelineContext context,
            boolean stopOnFailure,
            @Nullable Consumer<CompileDiagnostic> onDiagnostic) {
        List<CompiledPipeline> body = compile(engine, lines, null, onDiagnostic);
        return run(owner, engine, body, context, stopOnFailure);
    }

    /**
     * Runs one line at a time, threading the outcome forward.
     *
     * <p>Recursive continuation rather than a loop because each line completes asynchronously: a stage may
     * be dispatched to another thread or region, and the next line must not start until it finishes.</p>
     */
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

    /** Drops every cached compilation. Called when the action system reloads. */
    public void invalidate() {
        cache.clear();
    }

    /** {@return how many compilations are cached} */
    public int cachedCount() {
        return cache.size();
    }

    /**
     * Cache identity for one compiled line.
     *
     * @param engineIdentity identity of the engine that compiled it
     * @param line the pipeline text
     * @param phase the phase contract name
     */
    private record CacheKey(int engineIdentity, @NotNull String line, @NotNull String phase) {
    }
}
