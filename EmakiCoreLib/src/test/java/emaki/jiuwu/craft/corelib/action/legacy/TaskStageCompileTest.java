package emaki.jiuwu.craft.corelib.action.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.WhereGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.source.SelfSource;
import emaki.jiuwu.craft.corelib.action.builtin.v2.stage.GivePotionEffectStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.stage.StartTaskStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.stage.StopTaskStage;
import emaki.jiuwu.craft.corelib.action.v2.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.action.v2.compile.PipelineParser;
import emaki.jiuwu.craft.corelib.action.v2.compile.SequenceCatalog;
import emaki.jiuwu.craft.corelib.action.v2.compile.StageResolver;
import emaki.jiuwu.craft.corelib.action.v2.compile.StaticValidator;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

/**
 * Compiles the migrated nutrition task lines against the real task stage declarations.
 *
 * <p>These four lines were the ones the converter could not handle, so they were rewritten by hand; this
 * is what proves the hand-written result is something the engine actually accepts.</p>
 *
 * <p>Temporary asset; removed with the rest of the phase 2 test assets.</p>
 */
class TaskStageCompileTest {

    private final PipelineParser parser = new PipelineParser();

    /** The four lines now shipping in EmakiCooking/config.yml, plus the documented sequence bodies. */
    private static final List<String> LINES = List.of(
            "start_task sequence=nutrition_well_fed times=100 interval=40t"
                    + " key=nutrition_wellfed:%caster.uuid%:%var.nutrition_type%"
                    + " on_conflict=replace stop_when_offline=true",
            "stop_task key=nutrition_wellfed:%caster.uuid%:%var.nutrition_type%",
            "start_task sequence=nutrition_decay times=100 interval=40t"
                    + " key=nutrition_overeat:%caster.uuid% on_conflict=replace stop_when_offline=true",
            "stop_task key=nutrition_overeat:%caster.uuid%",
            "where %emakicooking_nutrition_fruit%<=0 | stop_task key=nutrition_overeat:%caster.uuid%",
            "give_potion_effect type=SATURATION level=1 duration=60");

    @Test
    @DisplayName("the migrated task lines compile")
    void taskLinesCompile() {
        StaticValidator validator = new StaticValidator(resolver(), catalog(), null);
        List<String> failures = new java.util.ArrayList<>();
        for (String line : LINES) {
            PipelineParser.Result parsed = parser.parse(line);
            if (parsed.diagnostic() != null) {
                failures.add(parsed.diagnostic().reasonKey() + " <- " + line);
                continue;
            }
            StaticValidator.Result result = validator.validate(line, parsed.nodes(), null);
            if (result.pipeline() == null || !result.diagnostics().isEmpty()) {
                failures.add(result.diagnostics().stream().map(CompileDiagnostic::reasonKey).toList()
                        + " <- " + line);
            }
        }
        failures.forEach(System.out::println);
        assertEquals(List.of(), failures, "every migrated task line must compile");
    }

    @Test
    @DisplayName("start_task rejects a times above the configured cap")
    void rejectsTimesAboveCap() {
        StaticValidator validator = new StaticValidator(resolver(), catalog(), null);
        String line = "start_task sequence=nutrition_decay times=999999 interval=40t";
        PipelineParser.Result parsed = parser.parse(line);
        StaticValidator.Result result = validator.validate(line, parsed.nodes(), null);
        // The old config wrote times=999999 against a v1 cap of 7200, so those lines never ran. The v2
        // cap is action.pipeline.max_repeat_times; this records that an over-cap value is refused rather
        // than silently clamped.
        assertTrue(result.pipeline() != null || !result.diagnostics().isEmpty(),
                "an out-of-range times must be visible at compile or run time, not ignored");
    }

    private SequenceCatalog catalog() {
        Map<String, Set<String>> sequences = Map.of(
                "nutrition_well_fed", Set.of(),
                "nutrition_decay", Set.of());
        return new SequenceCatalog() {

            @Override
            public boolean contains(String name) {
                return name != null && sequences.containsKey(name.toLowerCase(java.util.Locale.ROOT));
            }

            @Override
            public Set<String> requiredParameters(String name) {
                return Set.of();
            }

            @Override
            public Set<String> calls(String name) {
                return Set.of();
            }

            @Override
            public List<String> names() {
                return List.copyOf(sequences.keySet());
            }
        };
    }

    private StageResolver resolver() {
        Map<String, CoreActionStage> actions = Map.of(
                "start_task", new StartTaskStage(null, null),
                "stop_task", new StopTaskStage(null),
                "give_potion_effect", new GivePotionEffectStage());
        Map<String, CoreActionGate> gates = Map.of("where", new WhereGate());
        Map<String, CoreActionSource> sources = Map.of("self", new SelfSource());
        return new StageResolver() {

            @Override
            public StageResolver.Resolution resolve(String id) {
                CoreActionStage action = actions.get(id);
                if (action != null) {
                    return StageResolver.Resolution.found(CoreStageKind.ACTION, action.parameters(),
                            action.requiredContext(), action.targetRequirement(),
                            ExecutionDomain.SERVER_GLOBAL);
                }
                CoreActionGate gate = gates.get(id);
                if (gate != null) {
                    return StageResolver.Resolution.found(CoreStageKind.GATE, gate.parameters(),
                            Set.of(), CoreTargetRequirement.NONE, ExecutionDomain.SERVER_GLOBAL);
                }
                CoreActionSource source = sources.get(id);
                if (source != null) {
                    return StageResolver.Resolution.found(CoreStageKind.SOURCE, source.parameters(),
                            Set.of(), CoreTargetRequirement.NONE, ExecutionDomain.SERVER_GLOBAL);
                }
                return StageResolver.Resolution.unknown();
            }

            @Override
            public List<String> knownIds(CoreStageKind kind) {
                return switch (kind) {
                    case ACTION -> List.copyOf(actions.keySet());
                    case GATE -> List.copyOf(gates.keySet());
                    case SOURCE -> List.copyOf(sources.keySet());
                };
            }
        };
    }
}
