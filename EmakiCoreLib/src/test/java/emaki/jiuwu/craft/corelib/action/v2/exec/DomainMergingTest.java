package emaki.jiuwu.craft.corelib.action.v2.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.action.v2.PipelineContext;
import emaki.jiuwu.craft.corelib.action.v2.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.action.v2.compile.PipelineLimits;
import emaki.jiuwu.craft.corelib.action.v2.compile.PipelineParser;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

/**
 * Covers the merging rule from the runtime design: same-domain stages must share one dispatch, and one
 * dispatch per stage is explicitly called out as worse than v1.
 */
class DomainMergingTest {

    private final PipelineParser parser = new PipelineParser();
    private final Plugin owner = TestSubjects.enabledPlugin();

    private PipelineOutcome run(FakeStageInvoker invoker, CountingDispatcher dispatcher, String line) {
        ActionInterpreter interpreter = new ActionInterpreter(invoker, dispatcher.asStageDispatcher(),
                SequenceRepository.empty(), PipelineLimits.defaults());
        // A located caster is required: the region domain refuses a target with no world, which is the
        // dispatcher behaving correctly rather than something to work around.
        PipelineContext context = PipelineContext.root(owner, TestSubjects.locatedCaster(), null,
                "test", false, null);
        CompiledPipeline pipeline = new CompiledPipeline(line, parser.parse(line).nodes(), false);
        return interpreter.run(owner, pipeline, context).join();
    }

    @Test
    @DisplayName("three same-domain per-flow stages cost one dispatch, not three")
    void sameDomainStagesMerge() {
        CountingDispatcher dispatcher = new CountingDispatcher();
        FakeStageInvoker invoker = new FakeStageInvoker()
                .source("nearby", ExecutionDomain.LOCATION_REGION,
                        (context, arguments) -> CoreSourceResult.selected(List.of()))
                .gate("limit", ExecutionDomain.LOCATION_REGION, false,
                        (context, inbound, arguments) -> CoreGateResult.passed(inbound))
                .gate("sort_by", ExecutionDomain.LOCATION_REGION, false,
                        (context, inbound, arguments) -> CoreGateResult.passed(inbound));

        PipelineOutcome outcome = run(invoker, dispatcher, "nearby | limit 3 | sort_by distance");

        assertEquals(PipelineOutcome.Status.SUCCESS, outcome.status(),
                () -> "reason=" + outcome.reasonKey() + " args=" + outcome.args());
        assertEquals(1, dispatcher.count(),
                "three stages in one domain must share a dispatch: " + dispatcher.dispatches());
        assertEquals(List.of("nearby+limit+sort_by"), dispatcher.dispatches());
    }

    @Test
    @DisplayName("a pure gate folds into its neighbours without a dispatch of its own")
    void pureGateFolds() {
        CountingDispatcher dispatcher = new CountingDispatcher();
        FakeStageInvoker invoker = new FakeStageInvoker()
                .source("nearby", ExecutionDomain.LOCATION_REGION,
                        (context, arguments) -> CoreSourceResult.selected(List.of()))
                .gate("chance", ExecutionDomain.ASYNC_COMPUTE, true,
                        (context, inbound, arguments) -> CoreGateResult.passed(inbound));

        PipelineOutcome outcome = run(invoker, dispatcher, "nearby | chance 50%");

        assertEquals(PipelineOutcome.Status.SUCCESS, outcome.status());
        assertEquals(1, dispatcher.count(),
                "a PURE gate touches no Bukkit state, so it must not force its own dispatch: "
                        + dispatcher.dispatches());
    }

    @Test
    @DisplayName("a domain switch starts a new dispatch")
    void domainSwitchSplits() {
        CountingDispatcher dispatcher = new CountingDispatcher();
        TestSubjects.MutableEntity found = TestSubjects.entity("found");
        FakeStageInvoker invoker = new FakeStageInvoker()
                // The source runs in the region domain and yields an entity, so the entity-domain gate
                // that follows has a real entity to be scheduled onto.
                .source("nearby", ExecutionDomain.LOCATION_REGION,
                        (context, arguments) -> CoreSourceResult.selected(List.of(found.subject())))
                .gate("where", ExecutionDomain.ENTITY, false,
                        (context, inbound, arguments) -> CoreGateResult.passed(inbound));

        PipelineOutcome outcome = run(invoker, dispatcher, "nearby | where %target.health%<50");

        assertEquals(PipelineOutcome.Status.SUCCESS, outcome.status(),
                () -> "reason=" + outcome.reasonKey() + " args=" + outcome.args());
        assertEquals(2, dispatcher.count(),
                "a region-domain stage and an entity-domain stage cannot share a thread: "
                        + dispatcher.dispatches());
    }

    @Test
    @DisplayName("dispatch count does not grow with pipeline length while the domain holds")
    void dispatchCountDoesNotTrackStageCount() {
        CountingDispatcher three = new CountingDispatcher();
        FakeStageInvoker invoker = new FakeStageInvoker()
                .source("nearby", ExecutionDomain.LOCATION_REGION,
                        (context, arguments) -> CoreSourceResult.selected(List.of()))
                .gate("limit", ExecutionDomain.LOCATION_REGION, false,
                        (context, inbound, arguments) -> CoreGateResult.passed(inbound))
                .gate("sort_by", ExecutionDomain.LOCATION_REGION, false,
                        (context, inbound, arguments) -> CoreGateResult.passed(inbound))
                .gate("keep", ExecutionDomain.LOCATION_REGION, false,
                        (context, inbound, arguments) -> CoreGateResult.passed(inbound));

        run(invoker, three, "nearby | limit 3");
        int shortPipeline = three.count();

        CountingDispatcher four = new CountingDispatcher();
        run(invoker, four, "nearby | limit 3 | sort_by distance | keep");
        int longPipeline = four.count();

        assertEquals(shortPipeline, longPipeline,
                "adding same-domain stages must not add dispatches: " + four.dispatches());
    }

    @Test
    @DisplayName("a per-target action still dispatches once per target")
    void perTargetActionKeepsPerTargetDispatch() {
        CountingDispatcher dispatcher = new CountingDispatcher();
        TestSubjects.MutableEntity first = TestSubjects.entity("first");
        TestSubjects.MutableEntity second = TestSubjects.entity("second");
        FakeStageInvoker invoker = new FakeStageInvoker()
                .source("nearby", ExecutionDomain.LOCATION_REGION,
                        (context, arguments) -> CoreSourceResult.selected(
                                List.of(first.subject(), second.subject())))
                .action("damage", CoreTargetRequirement.REQUIRED_ENTITY,
                        (context, arguments) -> CoreActionOutcome.success());

        PipelineOutcome outcome = run(invoker, dispatcher, "nearby | damage amount=1");

        assertEquals(PipelineOutcome.Status.SUCCESS, outcome.status());
        assertEquals(3, dispatcher.count(),
                "the source shares one dispatch; each target owns the thread its action runs on: "
                        + dispatcher.dispatches());
    }

    @Test
    @DisplayName("a targetless action merges with same-domain neighbours")
    void targetlessActionMerges() {
        CountingDispatcher dispatcher = new CountingDispatcher();
        FakeStageInvoker invoker = new FakeStageInvoker()
                .action("broadcast", CoreTargetRequirement.NONE,
                        (context, arguments) -> CoreActionOutcome.success())
                .action("log", CoreTargetRequirement.NONE,
                        (context, arguments) -> CoreActionOutcome.success());

        PipelineOutcome outcome = run(invoker, dispatcher, "broadcast | log");

        assertEquals(PipelineOutcome.Status.SUCCESS, outcome.status());
        assertEquals(1, dispatcher.count(),
                "neither stage iterates targets, so both belong in one dispatch: "
                        + dispatcher.dispatches());
        assertTrue(dispatcher.dispatches().get(0).contains("broadcast"));
    }
}
