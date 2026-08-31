package emaki.jiuwu.craft.corelib.action.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.PhaseContract;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.TriggerContract;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompileDiagnostic;

public final class ActionLineRunner {

    private final Plugin owner;
    private final Supplier<ActionEngine> engineSupplier;
    private final PipelineBatchRunner batchRunner;
    private final PlaceholderBridge placeholders;
    private final Function<CompileDiagnostic, String> diagnosticFormatter;

    public ActionLineRunner(@NotNull Plugin owner,
            @NotNull Supplier<ActionEngine> engineSupplier,
            @NotNull PipelineBatchRunner batchRunner,
            @Nullable PlaceholderBridge placeholders) {
        this(owner, engineSupplier, batchRunner, placeholders, null);
    }

    public ActionLineRunner(@NotNull Plugin owner,
            @NotNull Supplier<ActionEngine> engineSupplier,
            @NotNull PipelineBatchRunner batchRunner,
            @Nullable PlaceholderBridge placeholders,
            @Nullable Function<CompileDiagnostic, String> diagnosticFormatter) {
        this.owner = owner;
        this.engineSupplier = engineSupplier;
        this.batchRunner = batchRunner;
        this.placeholders = placeholders == null ? PlaceholderBridge.noop() : placeholders;
        this.diagnosticFormatter = diagnosticFormatter == null
                ? CompileDiagnostic::reasonKey
                : diagnosticFormatter;
    }

    public @NotNull PipelineContext context(@Nullable Entity caster,
            @Nullable String phase,
            boolean silent,
            @Nullable Map<String, ?> placeholders) {
        Location origin = caster == null ? null : caster.getLocation();
        PipelineContext context = PipelineContext.root(owner,
                caster == null ? CoreActionSubject.absent() : CoreActionSubject.of(caster),
                origin, phase, silent, bridge());
        return placeholders == null || placeholders.isEmpty()
                ? context
                : context.withVariables(placeholders);
    }

    public @NotNull CompletableFuture<Boolean> run(@Nullable List<String> lines,
            @NotNull PipelineContext context,
            boolean stopOnFailure) {
        return run(lines, context, phaseContract(context), stopOnFailure);
    }

    public @NotNull CompletableFuture<Boolean> run(@Nullable List<String> lines,
            @NotNull PipelineContext context,
            @Nullable PhaseContract phase,
            boolean stopOnFailure) {
        ActionEngine engine = engineSupplier.get();
        if (engine == null) {
            logger().warning("Pipeline lines skipped: the action engine is not available yet.");
            return CompletableFuture.completedFuture(false);
        }
        PhaseContract resolved = phase == null ? phaseContract(context) : phase;
        return batchRunner.compileAndRun(owner, engine, lines, context, resolved, stopOnFailure,
                diagnostic -> logger().warning("Pipeline line rejected: "
                        + diagnosticFormatter.apply(diagnostic)));
    }

    public @NotNull CompletableFuture<Boolean> run(@Nullable List<String> lines,
            @NotNull PipelineContext context,
            @Nullable TriggerContract trigger,
            boolean stopOnFailure) {
        PhaseContract phase = trigger == null ? phaseContract(context) : trigger.phase(context.phase());
        return run(lines, context, phase, stopOnFailure);
    }

    public @NotNull CompletableFuture<Boolean> run(@Nullable List<String> lines,
            @Nullable Entity caster,
            @Nullable String phase,
            boolean silent,
            @Nullable Map<String, ?> placeholders,
            boolean stopOnFailure) {
        return run(lines, context(caster, phase, silent, placeholders), stopOnFailure);
    }

    public boolean available() {
        return engineSupplier.get() != null;
    }

    private PhaseContract phaseContract(PipelineContext context) {
        return PhaseContract.declared(context.phase(), Set.copyOf(context.presentKeys()),
                context.variables().keySet(), !context.targets().isEmpty());
    }

    private PlaceholderBridge bridge() {
        return placeholders;
    }

    private Logger logger() {
        return owner.getLogger();
    }
}
