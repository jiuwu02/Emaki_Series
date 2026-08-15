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

public final class SkillPipelineRuntime {

    private final EmakiSkillsPlugin plugin;
    private final SkillPlaceholderBridge placeholders;
    private final Map<String, CompiledSkill> cache = new ConcurrentHashMap<>();

    public SkillPipelineRuntime(EmakiSkillsPlugin plugin) {
        this.plugin = plugin;
        this.placeholders = new SkillPlaceholderBridge(plugin);
    }

    public SkillPlaceholderBridge placeholders() {
        return placeholders;
    }

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

    public void precompile(String skillId, SkillScriptDefinition script) {
        if (script != null && script.enabled()) {
            compiled(skillId, script);
        }
    }

    public List<PhaseDiagnostic> diagnostics() {
        List<PhaseDiagnostic> all = new ArrayList<>();
        cache.values().forEach(compiled -> all.addAll(compiled.diagnostics()));
        return List.copyOf(all);
    }

    public void invalidateAll() {
        cache.clear();
    }

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

    private void logCompileFailure(String skillId,
            SkillScriptPhase phase,
            int lineNumber,
            List<CompileDiagnostic> diagnostics) {
        if (plugin == null) {
            return;
        }

        String reason = plugin.coreLib() == null || plugin.coreLib().messageService() == null
                ? String.valueOf(diagnostics)
                : plugin.coreLib().messageService().renderFirstDiagnostic(diagnostics);
        plugin.getLogger().warning("Skill script error in '"
                + (Texts.isBlank(skillId) ? "unknown" : skillId) + "' phase " + phase.configKey()
                + " line " + lineNumber + ": " + reason);
    }

    private ActionEngine engine() {

        return plugin == null || plugin.coreLib() == null ? null : plugin.coreLib().actionEngine();
    }

    private record CompiledSkill(ActionEngine engine,
            Map<SkillScriptPhase, List<CompiledPipeline>> phases,
            List<PhaseDiagnostic> diagnostics) {

        private List<CompiledPipeline> phase(SkillScriptPhase phase) {
            return phases.getOrDefault(phase, List.of());
        }
    }

    public record PhaseDiagnostic(String skillId,
            SkillScriptPhase phase,
            int lineNumber,
            CompileDiagnostic diagnostic) {
    }
}
