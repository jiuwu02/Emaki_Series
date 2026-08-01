package emaki.jiuwu.craft.corelib.action.v2;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.v2.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.action.v2.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.action.v2.compile.PhaseContract;
import emaki.jiuwu.craft.corelib.action.v2.compile.PipelineLimits;
import emaki.jiuwu.craft.corelib.action.v2.compile.PipelineParser;
import emaki.jiuwu.craft.corelib.action.v2.compile.SequenceCatalog;
import emaki.jiuwu.craft.corelib.action.v2.compile.StageResolver;
import emaki.jiuwu.craft.corelib.action.v2.compile.StaticValidator;
import emaki.jiuwu.craft.corelib.action.v2.exec.ActionInterpreter;
import emaki.jiuwu.craft.corelib.action.v2.exec.PipelineOutcome;
import emaki.jiuwu.craft.corelib.action.v2.exec.SequenceRepository;
import emaki.jiuwu.craft.corelib.action.v2.exec.StageDispatcher;
import emaki.jiuwu.craft.corelib.action.v2.exec.StageInvoker;

/**
 * The single v2 entry point, replacing the v1 {@code ActionExecutor}.
 *
 * <p>Compilation and execution are separated on purpose: {@link #compile} runs at config load time and
 * {@link #run} walks an already validated AST. The v1 executor parsed each line on every execution.</p>
 */
public final class ActionEngine {

    private final PipelineParser parser = new PipelineParser();
    private final StaticValidator validator;
    private final ActionInterpreter interpreter;

    /**
     * Creates an engine.
     *
     * @param resolver stage metadata resolver used at compile time
     * @param invoker stage execution seam used at run time
     * @param dispatcher the single scheduling entry point
     * @param sequences named sequences available to {@code run}
     * @param limits compile limits
     */
    public ActionEngine(@NotNull StageResolver resolver,
            @NotNull StageInvoker invoker,
            @NotNull StageDispatcher dispatcher,
            @Nullable SequenceRepository sequences,
            @Nullable PipelineLimits limits) {
        SequenceCatalog catalog = sequences == null ? SequenceCatalog.empty() : sequences;
        this.validator = new StaticValidator(resolver, catalog, limits);
        this.interpreter = new ActionInterpreter(invoker, dispatcher, sequences, limits);
    }

    /**
     * Compiles one pipeline line.
     *
     * @param source pipeline text
     * @param phase what the triggering phase provides
     * @return the compile result
     */
    public @NotNull Result compile(@Nullable String source, @Nullable PhaseContract phase) {
        PipelineParser.Result parsed = parser.parse(source);
        if (parsed.diagnostic() != null) {
            return new Result(null, List.of(parsed.diagnostic()));
        }
        if (parsed.blank()) {
            return new Result(null, List.of(CompileDiagnostic.at("action.v2.validate.empty_pipeline", null)));
        }
        StaticValidator.Result validated = validator.validate(source, parsed.nodes(), phase);
        return new Result(validated.pipeline(), validated.diagnostics());
    }

    /**
     * Runs a compiled pipeline.
     *
     * @param owner plugin owning this invocation
     * @param pipeline the compiled pipeline
     * @param context root context
     * @return the pipeline outcome
     */
    public @NotNull CompletableFuture<PipelineOutcome> run(@NotNull Plugin owner,
            @NotNull CompiledPipeline pipeline,
            @NotNull PipelineContext context) {
        return interpreter.run(owner, pipeline, context);
    }

    /**
     * Compile outcome.
     *
     * @param pipeline compiled pipeline, {@code null} when compilation failed
     * @param diagnostics every detected problem
     */
    public record Result(@Nullable CompiledPipeline pipeline, @NotNull List<CompileDiagnostic> diagnostics) {

        public Result {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        /** {@return whether a usable pipeline was produced} */
        public boolean successful() {
            return pipeline != null && diagnostics.isEmpty();
        }
    }
}
