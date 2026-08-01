package emaki.jiuwu.craft.corelib.action.pipeline;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;

/**
 * What business modules use to run a list of configured pipeline lines.
 *
 * <p>This replaces holding an {@code ActionExecutor}. The distinction matters: the v1
 * executor was a stable object a module could keep for its lifetime, while {@link ActionEngine} is rebuilt
 * on every CoreLib reload because a reload installs a new stage table. A module that captured an engine at
 * construction would keep running against retired stages, and the symptom would be actions silently doing
 * nothing after a reload rather than an error.</p>
 *
 * <p>So this class holds a {@link Supplier} and reads it per call. Modules keep one of these for their
 * lifetime and never see the engine swap.</p>
 */
public final class ActionLineRunner {

    private final Plugin owner;
    private final Supplier<ActionEngine> engineSupplier;
    private final PipelineBatchRunner batchRunner;
    private final PlaceholderBridge placeholders;

    /**
     * Creates a runner.
     *
     * @param owner the plugin whose invocations these are
     * @param engineSupplier reads the live engine; must not capture it
     * @param batchRunner the shared batch runner, holding the compile cache
     * @param placeholders renders placeholders, may be {@code null} for variables-only rendering
     */
    public ActionLineRunner(@NotNull Plugin owner,
            @NotNull Supplier<ActionEngine> engineSupplier,
            @NotNull PipelineBatchRunner batchRunner,
            @Nullable PlaceholderBridge placeholders) {
        this.owner = owner;
        this.engineSupplier = engineSupplier;
        this.batchRunner = batchRunner;
        this.placeholders = placeholders == null ? PlaceholderBridge.noop() : placeholders;
    }

    /**
     * Builds a root context in the shape business triggers use.
     *
     * <p>Mirrors what the v1 {@code ActionContext.create(...).withPlaceholders(...)} produced, so a caller
     * migrating from v1 does not have to reason about the target flow: the caster is the acting player and
     * the pipeline's own source segment decides what it acts on.</p>
     *
     * @param caster the acting entity, may be {@code null} for server-side triggers
     * @param phase phase name, surfaced to stages and used in diagnostics
     * @param silent whether player-facing feedback is suppressed
     * @param placeholders values readable as {@code %var.name%}
     * @return the root context
     */
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

    /**
     * Compiles and runs a list of configured lines.
     *
     * @param lines the configured pipeline lines
     * @param context the root context
     * @param stopOnFailure whether the first failing line ends the batch
     * @return whether every executed line succeeded; {@code true} when there is nothing to run
     */
    public @NotNull CompletableFuture<Boolean> run(@Nullable List<String> lines,
            @NotNull PipelineContext context,
            boolean stopOnFailure) {
        ActionEngine engine = engineSupplier.get();
        if (engine == null) {
            logger().warning("Pipeline lines skipped: the action engine is not available yet.");
            return CompletableFuture.completedFuture(false);
        }
        return batchRunner.compileAndRun(owner, engine, lines, context, stopOnFailure,
                diagnostic -> logger().warning("Pipeline line rejected: " + diagnostic.reasonKey()));
    }

    /**
     * Compiles and runs in the common shape, building the context for the caller.
     *
     * @param lines the configured pipeline lines
     * @param caster the acting entity, may be {@code null}
     * @param phase phase name
     * @param silent whether player-facing feedback is suppressed
     * @param placeholders values readable as {@code %var.name%}
     * @param stopOnFailure whether the first failing line ends the batch
     * @return whether every executed line succeeded
     */
    public @NotNull CompletableFuture<Boolean> run(@Nullable List<String> lines,
            @Nullable Entity caster,
            @Nullable String phase,
            boolean silent,
            @Nullable Map<String, ?> placeholders,
            boolean stopOnFailure) {
        return run(lines, context(caster, phase, silent, placeholders), stopOnFailure);
    }

    /** {@return whether an engine is currently available} */
    public boolean available() {
        return engineSupplier.get() != null;
    }

    private PlaceholderBridge bridge() {
        return placeholders;
    }

    private Logger logger() {
        return owner.getLogger();
    }
}
