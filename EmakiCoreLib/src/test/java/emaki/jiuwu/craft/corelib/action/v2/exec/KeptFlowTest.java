package emaki.jiuwu.craft.corelib.action.v2.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.action.v2.ActionEngine;
import emaki.jiuwu.craft.corelib.action.v2.PipelineContext;
import emaki.jiuwu.craft.corelib.action.v2.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.action.v2.compile.PhaseContract;
import emaki.jiuwu.craft.corelib.action.v2.compile.StageResolver;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

/**
 * Verifies that {@code keep} is the cross-phase target channel phase 4 relies on.
 *
 * <p>Worth testing because the failure mode is silent and order-dependent: if the handoff read the pipeline's
 * final flow instead of the {@code keep} point, moving a {@code send_message} line after {@code keep} would
 * hand the caster to the next phase and turn a miss into a hit. Every assertion here is about that
 * distinction.</p>
 *
 * <p>Temporary asset for phase 4 verification.</p>
 */
class KeptFlowTest {

    private final TestSubjects.MutableEntity first = TestSubjects.entity("first");
    private final TestSubjects.MutableEntity second = TestSubjects.entity("second");
    private final TestSubjects.MutableEntity caster = TestSubjects.entity("caster");

    /**
     * An invoker whose stage table mirrors the real one closely enough for the handoff.
     *
     * <p>{@code keep} passes its inbound flow through, exactly as {@code KeepGate} does; {@code pick} stands in
     * for {@code looking_at}, {@code self} for the implicit caster source, and {@code limit_one} for any gate
     * that narrows the flow after {@code keep}.</p>
     */
    private FakeStageInvoker invoker() {
        return new FakeStageInvoker()
                .source("pick", (context, arguments) -> selected(first, second))
                .source("self", (context, arguments) -> selected(caster))
                .source("none", (context, arguments) -> CoreSourceResult.selected(List.of()))
                .gate("keep", (context, inbound, arguments) ->
                        CoreGateResult.passed(new ArrayList<>(inbound)))
                .gate("limit_one", (context, inbound, arguments) -> CoreGateResult.passed(
                        inbound.isEmpty() ? List.of() : List.of(inbound.get(0))))
                .action("noop", CoreTargetRequirement.OPTIONAL,
                        (context, arguments) -> CoreActionOutcome.success());
    }

    private static CoreSourceResult selected(TestSubjects.MutableEntity... entities) {
        List<CoreActionSubject> subjects = new ArrayList<>(entities.length);
        for (TestSubjects.MutableEntity entity : entities) {
            subjects.add(entity.subject());
        }
        return CoreSourceResult.selected(subjects);
    }

    private ActionEngine engine(FakeStageInvoker invoker) {
        return new ActionEngine(new FakeResolver(invoker), invoker, StageDispatcher.inline(), null, null);
    }

    private PipelineOutcome run(String line) {
        FakeStageInvoker invoker = invoker();
        ActionEngine engine = engine(invoker);
        ActionEngine.Result compiled = engine.compile(line, PhaseContract.permissive("cast"));
        assertTrue(compiled.successful(), () -> "should compile: " + line + " -> " + compiled.diagnostics());
        CompiledPipeline pipeline = compiled.pipeline();
        assertNotNull(pipeline);
        PipelineContext context = PipelineContext.root(TestSubjects.enabledPlugin(),
                TestSubjects.locatedCaster(), null, "cast", false, null);
        return engine.run(TestSubjects.enabledPlugin(), pipeline, context).join();
    }

    private static List<String> names(List<CoreActionSubject> flow) {
        List<String> result = new ArrayList<>(flow.size());
        flow.forEach(subject -> result.add(String.valueOf(subject.entityOrNull())));
        return result;
    }

    @Test
    void aPipelineWithoutKeepReportsAnEmptyKeptFlow() {
        // The Skills session leaves its targets untouched in this case, which is what makes "no keep" mean
        // "do not change what the next phase sees" rather than "clear it".
        PipelineOutcome outcome = run("pick | noop");

        assertTrue(outcome.keptFlow().isEmpty());
    }

    @Test
    void keepReportsTheFlowItSaw() {
        PipelineOutcome outcome = run("pick | keep");

        assertEquals(List.of("first", "second"), names(outcome.keptFlow()));
    }

    @Test
    void aLaterGateDoesNotChangeWhatKeepRecorded() {
        // keep means "the flow at this point". A narrowing gate after it is a statement about the rest of this
        // line, not about the handoff.
        PipelineOutcome outcome = run("pick | keep | limit_one | noop");

        assertEquals(List.of("first", "second"), names(outcome.keptFlow()));
    }

    @Test
    void theLastKeepWins() {
        PipelineOutcome outcome = run("pick | keep | limit_one | keep | noop");

        assertEquals(List.of("first"), names(outcome.keptFlow()));
    }

    @Test
    void keepRecordsAnEmptyFlowWhenItSawNothing() {
        // This is the MISS case: the source found nothing, keep records that, and the Skills session must not
        // read a non-empty final flow from a later line and call it a hit.
        PipelineOutcome outcome = run("none | keep");

        assertNotNull(outcome.keptFlow());
        assertTrue(outcome.keptFlow().isEmpty());
    }

    @Test
    void keepInsideABranchBodyIsStillRecorded() {
        // The interpreter records at the single point every gate result converges, so a branch body is covered
        // without the branch code knowing about keep.
        PipelineOutcome outcome = run("self | if 3>2 [ pick | keep ]");

        assertEquals(List.of("first", "second"), names(outcome.keptFlow()));
    }

    /** Resolves stage metadata straight out of the fake invoker, so both views agree on the stage table. */
    private record FakeResolver(FakeStageInvoker invoker) implements StageResolver {

        @Override
        public StageResolver.Resolution resolve(String id) {
            StageInvoker.Handle handle = invoker.resolve(id);
            return handle == null
                    ? StageResolver.Resolution.unknown()
                    : StageResolver.Resolution.found(handle.kind(), handle.parameters(), Set.of(),
                            handle.targetRequirement(), ExecutionDomain.SERVER_GLOBAL);
        }

        @Override
        public List<String> knownIds(CoreStageKind kind) {
            return List.of();
        }
    }

    @Test
    void theOutcomeFactoriesKeepTheirOldSignatures() {
        // The four no-keptFlow factories still exist so phase 2/3 call sites did not have to change.
        assertSame(PipelineOutcome.Status.SUCCESS, PipelineOutcome.success(List.of()).status());
        assertTrue(PipelineOutcome.success(List.of()).keptFlow().isEmpty());
        assertTrue(PipelineOutcome.skipped("x", List.of()).keptFlow().isEmpty());
        assertTrue(PipelineOutcome.partial("x", Map.of(), List.of()).keptFlow().isEmpty());
        assertTrue(PipelineOutcome.failure(null, "x", Map.of(), List.of()).keptFlow().isEmpty());
    }
}
