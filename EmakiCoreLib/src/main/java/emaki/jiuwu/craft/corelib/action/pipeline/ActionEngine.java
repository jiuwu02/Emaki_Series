package emaki.jiuwu.craft.corelib.action.pipeline;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.PhaseContract;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.PipelineLimits;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.PipelineParser;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.SequenceCatalog;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.StageResolver;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.StaticValidator;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.ActionInterpreter;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.PipelineOutcome;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.SequenceRepository;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.StageDispatcher;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.StageInvoker;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.StepReporter;

public final class ActionEngine {

    private final PipelineParser parser;
    private final StaticValidator validator;
    private final ActionInterpreter interpreter;
    private final StageResolver resolver;
    private final StageInvoker invoker;
    private final StageDispatcher dispatcher;
    private final SequenceRepository sequences;
    private final PipelineLimits limits;
    private final Map<String, String> aliases;

    public ActionEngine(@NotNull StageResolver resolver,
            @NotNull StageInvoker invoker,
            @NotNull StageDispatcher dispatcher,
            @Nullable SequenceRepository sequences,
            @Nullable PipelineLimits limits) {
        this(resolver, invoker, dispatcher, sequences, limits, null, false, null);
    }

    public ActionEngine(@NotNull StageResolver resolver,
            @NotNull StageInvoker invoker,
            @NotNull StageDispatcher dispatcher,
            @Nullable SequenceRepository sequences,
            @Nullable PipelineLimits limits,
            @Nullable Map<String, String> aliases) {
        this(resolver, invoker, dispatcher, sequences, limits, null, false, aliases);
    }

    private ActionEngine(@NotNull StageResolver resolver,
            @NotNull StageInvoker invoker,
            @NotNull StageDispatcher dispatcher,
            @Nullable SequenceRepository sequences,
            @Nullable PipelineLimits limits,
            @Nullable StepReporter reporter,
            boolean dryRun,
            @Nullable Map<String, String> aliases) {
        SequenceCatalog catalog = sequences == null ? SequenceCatalog.empty() : sequences;
        this.resolver = resolver;
        this.invoker = invoker;
        this.dispatcher = dispatcher;
        this.sequences = sequences;
        this.limits = limits;
        this.aliases = aliases;
        this.parser = aliases == null || aliases.isEmpty()
                ? new PipelineParser()
                : new PipelineParser(aliases);
        this.validator = new StaticValidator(resolver, catalog, limits);
        this.interpreter = new ActionInterpreter(invoker, dispatcher, sequences, limits,
                reporter, dryRun);
    }

    public @NotNull ActionEngine withReporter(@Nullable StepReporter reporter, boolean dryRun) {
        return new ActionEngine(resolver, invoker, dispatcher, sequences, limits,
                reporter, dryRun, aliases);
    }

    public @NotNull Result compile(@Nullable String source, @Nullable PhaseContract phase) {
        PipelineParser.Result parsed = parser.parse(source);
        if (parsed.diagnostic() != null) {
            return new Result(null, List.of(parsed.diagnostic()));
        }
        if (parsed.blank()) {
            return new Result(null, List.of(CompileDiagnostic.at("action.validate.empty_pipeline", null)));
        }
        StaticValidator.Result validated = validator.validate(source, parsed.nodes(), phase);
        return new Result(validated.pipeline(), validated.diagnostics());
    }

    public @NotNull CompletableFuture<PipelineOutcome> run(@NotNull Plugin owner,
            @NotNull CompiledPipeline pipeline,
            @NotNull PipelineContext context) {
        return interpreter.run(owner, pipeline, context);
    }

    public record Result(@Nullable CompiledPipeline pipeline, @NotNull List<CompileDiagnostic> diagnostics) {

        public Result {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        public boolean successful() {
            return pipeline != null && diagnostics.isEmpty();
        }
    }
}
