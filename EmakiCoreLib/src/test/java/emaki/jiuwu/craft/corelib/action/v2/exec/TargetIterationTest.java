package emaki.jiuwu.craft.corelib.action.v2.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.action.v2.PipelineContext;
import emaki.jiuwu.craft.corelib.action.v2.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.action.v2.compile.PipelineLimits;
import emaki.jiuwu.craft.corelib.action.v2.compile.PipelineParser;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;

/**
 * Covers the four target-iteration boundaries phase 2 requires: zero targets, iteration order, partial
 * failure, and a target going invalid part-way through.
 */
class TargetIterationTest {

    private final PipelineParser parser = new PipelineParser();
    private final Plugin owner = TestSubjects.enabledPlugin();

    private CompiledPipeline compile(String line) {
        PipelineParser.Result parsed = parser.parse(line);
        assertNotNull(parsed.nodes());
        return new CompiledPipeline(line, parsed.nodes(), false);
    }

    private PipelineOutcome run(FakeStageInvoker invoker, String line) {
        ActionInterpreter interpreter = new ActionInterpreter(invoker, StageDispatcher.inline(),
                SequenceRepository.empty(), PipelineLimits.defaults());
        PipelineContext context = PipelineContext.root(owner, CoreActionSubject.absent(), null,
                "test", false, null);
        return interpreter.run(owner, compile(line), context).join();
    }

    @Test
    @DisplayName("zero targets: a stage that requires one is skipped, not failed")
    void zeroTargetsSkipsRequiringStage() {
        FakeStageInvoker invoker = new FakeStageInvoker()
                .source("nearby", (context, arguments) -> CoreSourceResult.selected(List.of()))
                .action("damage", CoreTargetRequirement.REQUIRED_ENTITY,
                        (context, arguments) -> CoreActionOutcome.success());

        PipelineOutcome outcome = run(invoker, "nearby | damage amount=1");

        assertEquals(PipelineOutcome.Status.SKIPPED, outcome.status());
        assertTrue(invoker.invocationLog().stream().noneMatch(entry -> entry.startsWith("damage")),
                "a stage requiring a target must not run against an empty flow");
    }

    @Test
    @DisplayName("zero targets: a stage requiring none still runs exactly once")
    void zeroTargetsStillRunsTargetlessStage() {
        FakeStageInvoker invoker = new FakeStageInvoker()
                .source("nearby", (context, arguments) -> CoreSourceResult.selected(List.of()))
                .action("broadcast", CoreTargetRequirement.NONE,
                        (context, arguments) -> CoreActionOutcome.success());

        PipelineOutcome outcome = run(invoker, "nearby | broadcast");

        assertEquals(PipelineOutcome.Status.SUCCESS, outcome.status());
        assertEquals(List.of("nearby", "broadcast#0"), invoker.invocationLog());
    }

    @Test
    @DisplayName("order: targets are visited in source order, once each")
    void iterationFollowsSourceOrder() {
        TestSubjects.MutableEntity first = TestSubjects.entity("first");
        TestSubjects.MutableEntity second = TestSubjects.entity("second");
        TestSubjects.MutableEntity third = TestSubjects.entity("third");
        FakeStageInvoker invoker = new FakeStageInvoker()
                .source("nearby", (context, arguments) -> CoreSourceResult.selected(
                        List.of(first.subject(), second.subject(), third.subject())))
                .action("damage", CoreTargetRequirement.REQUIRED_ENTITY,
                        (context, arguments) -> CoreActionOutcome.success());

        PipelineOutcome outcome = run(invoker, "nearby | damage amount=1");

        assertEquals(PipelineOutcome.Status.SUCCESS, outcome.status());
        assertEquals(List.of("nearby", "damage#0", "damage#1", "damage#2"), invoker.invocationLog());
    }

    @Test
    @DisplayName("partial failure: some targets failing yields PARTIAL and does not stop the pipeline")
    void partialFailureContinuesPipeline() {
        TestSubjects.MutableEntity first = TestSubjects.entity("first");
        TestSubjects.MutableEntity second = TestSubjects.entity("second");
        FakeStageInvoker invoker = new FakeStageInvoker()
                .source("nearby", (context, arguments) -> CoreSourceResult.selected(
                        List.of(first.subject(), second.subject())))
                .action("damage", CoreTargetRequirement.REQUIRED_ENTITY,
                        (context, arguments) -> context.currentTargetIndex() == 1
                                ? CoreActionOutcome.failure(CoreActionFailureKind.REJECTED, "immune")
                                : CoreActionOutcome.success())
                .action("ignite", CoreTargetRequirement.REQUIRED_ENTITY,
                        (context, arguments) -> CoreActionOutcome.success());

        PipelineOutcome outcome = run(invoker, "nearby | damage amount=1 | ignite");

        assertEquals(PipelineOutcome.Status.PARTIAL, outcome.status());
        assertTrue(invoker.invocationLog().containsAll(List.of("ignite#0", "ignite#1")),
                "a partial result must not stop the following stage: " + invoker.invocationLog());
    }

    @Test
    @DisplayName("all targets failing yields FAILURE and stops the pipeline")
    void totalFailureStopsPipeline() {
        TestSubjects.MutableEntity only = TestSubjects.entity("only");
        FakeStageInvoker invoker = new FakeStageInvoker()
                .source("nearby", (context, arguments) -> CoreSourceResult.selected(List.of(only.subject())))
                .action("damage", CoreTargetRequirement.REQUIRED_ENTITY,
                        (context, arguments) -> CoreActionOutcome.failure(
                                CoreActionFailureKind.REJECTED, "immune"))
                .action("ignite", CoreTargetRequirement.REQUIRED_ENTITY,
                        (context, arguments) -> CoreActionOutcome.success());

        PipelineOutcome outcome = run(invoker, "nearby | damage amount=1 | ignite");

        assertEquals(PipelineOutcome.Status.FAILURE, outcome.status());
        assertEquals(CoreActionFailureKind.REJECTED, outcome.failureKind());
        assertTrue(invoker.invocationLog().stream().noneMatch(entry -> entry.startsWith("ignite")),
                "a fully failed stage must stop the pipeline: " + invoker.invocationLog());
    }

    @Test
    @DisplayName("invalidated mid-iteration: a target that dies during the loop is skipped, others still run")
    void targetInvalidatedMidIteration() {
        TestSubjects.MutableEntity first = TestSubjects.entity("first");
        TestSubjects.MutableEntity second = TestSubjects.entity("second");
        TestSubjects.MutableEntity third = TestSubjects.entity("third");
        FakeStageInvoker invoker = new FakeStageInvoker()
                .source("nearby", (context, arguments) -> CoreSourceResult.selected(
                        List.of(first.subject(), second.subject(), third.subject())))
                .action("damage", CoreTargetRequirement.REQUIRED_ENTITY, (context, arguments) -> {
                    if (context.currentTargetIndex() == 0) {
                        // Models a chain effect killing a later target while the loop is still running.
                        second.invalidate();
                    }
                    return CoreActionOutcome.success();
                });

        PipelineOutcome outcome = run(invoker, "nearby | damage amount=1");

        assertEquals(PipelineOutcome.Status.SUCCESS, outcome.status());
        assertEquals(List.of("nearby", "damage#0", "damage#2"), invoker.invocationLog(),
                "the invalidated target must be skipped without shifting the remaining indices");
    }

    @Test
    @DisplayName("invalidated mid-iteration: every target dying yields SKIPPED, not FAILURE")
    void allTargetsInvalidated() {
        TestSubjects.MutableEntity first = TestSubjects.entity("first");
        TestSubjects.MutableEntity second = TestSubjects.entity("second");
        first.invalidate();
        second.invalidate();
        FakeStageInvoker invoker = new FakeStageInvoker()
                .source("nearby", (context, arguments) -> CoreSourceResult.selected(
                        List.of(first.subject(), second.subject())))
                .action("damage", CoreTargetRequirement.REQUIRED_ENTITY,
                        (context, arguments) -> CoreActionOutcome.success());

        PipelineOutcome outcome = run(invoker, "nearby | damage amount=1");

        assertEquals(PipelineOutcome.Status.SUCCESS, outcome.status(),
                "losing every target is a gameplay state, not a configuration error");
        assertEquals(List.of("nearby"), invoker.invocationLog());
        assertEquals(PipelineOutcome.Status.SKIPPED, outcome.stageResults().get(1).status());
    }
}
