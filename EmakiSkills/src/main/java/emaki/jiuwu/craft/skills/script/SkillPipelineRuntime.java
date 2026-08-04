package emaki.jiuwu.craft.skills.script;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.corelib.action.pipeline.ActionEngine;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.PhaseContract;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.PipelineOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;

/**
 * Compiles skill script lines at load time and runs them through CoreLib's {@link ActionEngine}.
 *
 * <p>Replaces {@code SkillScriptExecutor}, which parsed every line on every cast and carried its own
 * scheduling, timeout, chance, delay and condition handling. All of that now lives in the pipeline, so this
 * class only owns the compile cache and the per-line iteration.</p>
 *
 * <p>A line that fails to compile is logged and skipped rather than aborting its phase, matching what the v1
 * executor did with a syntax error. Its diagnostics are retained so the config precheck can report them
 * without compiling a second time.</p>
 */
public final class SkillPipelineRuntime {

    private final EmakiSkillsPlugin plugin;
    private final SkillPlaceholderBridge placeholders;
    private final Map<String, CompiledSkill> cache = new ConcurrentHashMap<>();

    /**
     * Creates the runtime.
     *
     * @param plugin the owning plugin, used to reach CoreLib's engine
     */
    public SkillPipelineRuntime(EmakiSkillsPlugin plugin) {
        this.plugin = plugin;
        this.placeholders = new SkillPlaceholderBridge(plugin);
    }

    /** {@return the placeholder bridge to install on root contexts} */
    public SkillPlaceholderBridge placeholders() {
        return placeholders;
    }

    /**
     * Runs one phase of a skill script.
     *
     * <p>Lines run in order. {@code stop_on_failure} stops at the first failing line; otherwise every line
     * runs and the last one's outcome is returned. An empty phase yields a success with no stage results.</p>
     *
     * @param skillId the skill being cast, used as the compile cache key
     * @param script the script definition
     * @param phase the phase to run
     * @param context the root context for this phase
     * @return the outcome of the last line that ran
     */
    public CompletableFuture<PipelineOutcome> runPhase(String skillId,
            SkillScriptDefinition script,
            SkillScriptPhase phase,
            PipelineContext context) {
        if (script == null || phase == null || context == null) {
            return CompletableFuture.completedFuture(PipelineOutcome.success(List.of()));
        }
        CompiledSkill compiled = compiled(skillId, script);
        if (compiled == null) {
            return CompletableFuture.completedFuture(PipelineOutcome.failure(
                    CoreActionFailureKind.OWNER_DISABLED, "action.run.stage_unavailable",
                    Map.of(), List.of()));
        }
        List<CompiledPipeline> pipelines = compiled.phase(phase);
        if (pipelines.isEmpty()) {
            return CompletableFuture.completedFuture(PipelineOutcome.success(List.of()));
        }
        return advance(compiled.engine(), pipelines, 0, script.stopOnFailure(), context,
                PipelineOutcome.success(List.of()));
    }

    private CompletableFuture<PipelineOutcome> advance(ActionEngine engine,
            List<CompiledPipeline> pipelines,
            int index,
            boolean stopOnFailure,
            PipelineContext context,
            PipelineOutcome previous) {
        if (index >= pipelines.size()) {
            return CompletableFuture.completedFuture(previous);
        }
        return engine.run(plugin, pipelines.get(index), context).thenCompose(outcome -> {
            PipelineOutcome safe = outcome == null ? PipelineOutcome.success(List.of()) : outcome;
            if (stopOnFailure && safe.status() == PipelineOutcome.Status.FAILURE) {
                return CompletableFuture.completedFuture(safe);
            }
            return advance(engine, pipelines, index + 1, stopOnFailure, context, safe);
        });
    }

    /**
     * Compiles every phase of a script so the config precheck can read its diagnostics.
     *
     * @param skillId the skill id
     * @param script the script definition
     */
    public void precompile(String skillId, SkillScriptDefinition script) {
        if (script != null && script.enabled()) {
            compiled(skillId, script);
        }
    }

    /** {@return every diagnostic collected while compiling, one entry per problem} */
    public List<PhaseDiagnostic> diagnostics() {
        List<PhaseDiagnostic> all = new ArrayList<>();
        cache.values().forEach(compiled -> all.addAll(compiled.diagnostics()));
        return List.copyOf(all);
    }

    /**
     * Drops every cached pipeline.
     *
     * <p>Called from the Skills reload path: a reload rereads the skill YAML, so the text that was compiled is
     * no longer necessarily what is configured.</p>
     */
    public void invalidateAll() {
        cache.clear();
    }

    /**
     * Returns the cache entry for one skill, compiling all four phases on first use.
     *
     * <p>Recompiles when CoreLib swapped in a new engine. {@code installStageRuntime} builds a fresh
     * {@link ActionEngine} on every CoreLib reload, so an engine identity change is a reliable signal that the
     * stage table may have changed shape; a cached pipeline validated against the old parameter declarations
     * would otherwise only fail at run time.</p>
     */
    private CompiledSkill compiled(String skillId, SkillScriptDefinition script) {
        ActionEngine engine = engine();
        if (engine == null) {
            return null;
        }
        String key = Texts.isBlank(skillId) ? "" : Texts.normalizeId(skillId);
        return cache.compute(key, (ignored, existing) -> existing != null && existing.engine() == engine
                ? existing
                : compile(engine, key, script));
    }

    private CompiledSkill compile(ActionEngine engine, String skillId, SkillScriptDefinition script) {
        Map<SkillScriptPhase, List<CompiledPipeline>> phases = new EnumMap<>(SkillScriptPhase.class);
        List<PhaseDiagnostic> diagnostics = new ArrayList<>();
        int maxLines = plugin.appConfig().scriptEngine().maxLinesPerPhase();
        for (SkillScriptPhase phase : SkillScriptPhase.values()) {
            List<String> lines = script.lines(phase);
            if (lines.isEmpty()) {
                continue;
            }
            PhaseContract contract = PhaseContract.permissive(phase.configKey());
            List<CompiledPipeline> compiled = new ArrayList<>(lines.size());
            for (int index = 0; index < Math.min(lines.size(), maxLines); index++) {
                ActionEngine.Result result = engine.compile(lines.get(index), contract);
                if (result.successful()) {
                    compiled.add(result.pipeline());
                    continue;
                }
                int lineNumber = index + 1;
                result.diagnostics().forEach(diagnostic ->
                        diagnostics.add(new PhaseDiagnostic(skillId, phase, lineNumber, diagnostic)));
                logCompileFailure(skillId, phase, lineNumber, result.diagnostics());
            }
            phases.put(phase, List.copyOf(compiled));
        }
        return new CompiledSkill(engine, Map.copyOf(phases), List.copyOf(diagnostics));
    }

    /**
     * Logs a script problem the server owner has to fix in the skill YAML.
     *
     * <p>Names the skill, phase and line so it can be located directly, the same contract the v1
     * {@code logConfigurationError} had.</p>
     */
    private void logCompileFailure(String skillId,
            SkillScriptPhase phase,
            int lineNumber,
            List<CompileDiagnostic> diagnostics) {
        if (plugin == null) {
            return;
        }
        plugin.getLogger().warning("Skill script error in '"
                + (Texts.isBlank(skillId) ? "unknown" : skillId) + "' phase " + phase.configKey()
                + " line " + lineNumber + ": " + diagnostics);
    }

    private ActionEngine engine() {
        // Read through on every use rather than holding a field: a CoreLib reload replaces the engine, and a
        // stale reference would keep dispatching into the retired stage table.
        return plugin == null || plugin.coreLib() == null ? null : plugin.coreLib().actionEngine();
    }

    /**
     * One skill's compiled phases, tied to the engine they were compiled against.
     *
     * @param engine the engine identity this entry is valid for
     * @param phases compiled lines per phase
     * @param diagnostics problems found while compiling
     */
    private record CompiledSkill(ActionEngine engine,
            Map<SkillScriptPhase, List<CompiledPipeline>> phases,
            List<PhaseDiagnostic> diagnostics) {

        private List<CompiledPipeline> phase(SkillScriptPhase phase) {
            return phases.getOrDefault(phase, List.of());
        }
    }

    /**
     * One compile problem located in a skill's script.
     *
     * @param skillId the skill whose script failed to compile
     * @param phase the phase the line belongs to
     * @param lineNumber one-based line number within that phase
     * @param diagnostic what CoreLib reported
     */
    public record PhaseDiagnostic(String skillId,
            SkillScriptPhase phase,
            int lineNumber,
            CompileDiagnostic diagnostic) {
    }
}
