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
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

/**
 * Covers the runtime side of the timing stages: {@code after} defers the stages that follow it and
 * {@code every} repeats them, both revalidating before each run.
 */
class TimingStageTest {

    private final PipelineParser parser = new PipelineParser();
    private final Plugin owner = TestSubjects.enabledPlugin();

    private FakeStageInvoker timingInvoker() {
        return new FakeStageInvoker()
                .gate("after", ExecutionDomain.SERVER_GLOBAL, true,
                        (context, inbound, arguments) -> CoreGateResult.passed(inbound),
                        CoreStageParameter.positional("delay", CoreStageParameterType.TIME, ""))
                .gate("every", ExecutionDomain.SERVER_GLOBAL, true,
                        (context, inbound, arguments) -> CoreGateResult.passed(inbound),
                        CoreStageParameter.positional("interval", CoreStageParameterType.TIME, ""),
                        CoreStageParameter.optional("times", CoreStageParameterType.INTEGER, "0", ""));
    }

    private PipelineOutcome run(FakeStageInvoker invoker, CountingDispatcher dispatcher, String line) {
        ActionInterpreter interpreter = new ActionInterpreter(invoker, dispatcher.asStageDispatcher(),
                SequenceRepository.empty(), PipelineLimits.defaults());
        PipelineContext context = PipelineContext.root(owner, TestSubjects.locatedCaster(), null,
                "test", false, null);
        CompiledPipeline pipeline = new CompiledPipeline(line, parser.parse(line).nodes(), false);
        return interpreter.run(owner, pipeline, context).join();
    }

    @Test
    @DisplayName("after defers the stages that follow it by the written delay")
    void afterDelaysFollowingStages() {
        CountingDispatcher dispatcher = new CountingDispatcher();
        FakeStageInvoker invoker = timingInvoker()
                .action("send_message", CoreTargetRequirement.NONE,
                        (context, arguments) -> CoreActionOutcome.success());

        PipelineOutcome outcome = run(invoker, dispatcher, "after 10t | send_message text=hi");

        assertEquals(PipelineOutcome.Status.SUCCESS, outcome.status(),
                () -> "reason=" + outcome.reasonKey() + " args=" + outcome.args());
        assertTrue(dispatcher.dispatches().contains("delay@10"),
                "the delay must reach the scheduler as 10 ticks: " + dispatcher.dispatches());
        assertEquals(List.of("send_message#0"), invoker.invocationLog(),
                "the deferred body still runs exactly once");
    }

    @Test
    @DisplayName("a delay expressed in seconds converts to ticks")
    void secondsConvertToTicks() {
        CountingDispatcher dispatcher = new CountingDispatcher();
        FakeStageInvoker invoker = timingInvoker()
                .action("send_message", CoreTargetRequirement.NONE,
                        (context, arguments) -> CoreActionOutcome.success());

        run(invoker, dispatcher, "after 2s | send_message text=hi");

        assertTrue(dispatcher.dispatches().contains("delay@40"),
                "2s is 40 ticks: " + dispatcher.dispatches());
    }

    @Test
    @DisplayName("every with times 0 runs the body once, matching the D4 default")
    void everyWithoutTimesRunsOnce() {
        CountingDispatcher dispatcher = new CountingDispatcher();
        FakeStageInvoker invoker = timingInvoker()
                .action("spawn_particle", CoreTargetRequirement.NONE,
                        (context, arguments) -> CoreActionOutcome.success());

        PipelineOutcome outcome = run(invoker, dispatcher, "every 20t | spawn_particle particle=flame");

        assertEquals(PipelineOutcome.Status.SUCCESS, outcome.status(),
                () -> "reason=" + outcome.reasonKey() + " args=" + outcome.args());
        assertEquals(1, invoker.invocationLog().size(),
                "times defaults to 0, which means no repeats: " + invoker.invocationLog());
    }

    @Test
    @DisplayName("every times 3 runs the body four times: the first run plus three repeats")
    void everyRepeatsBody() {
        CountingDispatcher dispatcher = new CountingDispatcher();
        FakeStageInvoker invoker = timingInvoker()
                .action("spawn_particle", CoreTargetRequirement.NONE,
                        (context, arguments) -> CoreActionOutcome.success());

        PipelineOutcome outcome = run(invoker, dispatcher,
                "every 20t times 3 | spawn_particle particle=flame");

        assertEquals(PipelineOutcome.Status.SUCCESS, outcome.status(),
                () -> "reason=" + outcome.reasonKey() + " args=" + outcome.args());
        assertEquals(4, invoker.invocationLog().size(),
                "one initial run plus three repeats: " + invoker.invocationLog());
        assertEquals(4, dispatcher.dispatches().stream().filter("delay@20"::equals).count(),
                "each run waits its interval: " + dispatcher.dispatches());
    }

    @Test
    @DisplayName("every repeats the whole remainder, not just the last stage")
    void everyRepeatsEveryFollowingStage() {
        CountingDispatcher dispatcher = new CountingDispatcher();
        FakeStageInvoker invoker = timingInvoker()
                .action("first", CoreTargetRequirement.NONE,
                        (context, arguments) -> CoreActionOutcome.success())
                .action("second", CoreTargetRequirement.NONE,
                        (context, arguments) -> CoreActionOutcome.success());

        run(invoker, dispatcher, "every 5t times 1 | first | second");

        assertEquals(List.of("first#0", "second#0", "first#0", "second#0"), invoker.invocationLog(),
                "a timing stage governs every stage after it: " + invoker.invocationLog());
    }

    @Test
    @DisplayName("a target lost during the delay yields SKIPPED, not FAILURE")
    void targetLostDuringDelaySkips() {
        CountingDispatcher dispatcher = new CountingDispatcher();
        TestSubjects.MutableEntity victim = TestSubjects.entity("victim");
        FakeStageInvoker invoker = timingInvoker()
                .source("nearby", ExecutionDomain.SERVER_GLOBAL, (context, arguments) -> {
                    // Models the entity dying between selection and the deferred stage.
                    victim.invalidate();
                    return CoreSourceResult.selected(List.of(victim.subject()));
                })
                .action("damage", CoreTargetRequirement.REQUIRED_ENTITY,
                        (context, arguments) -> CoreActionOutcome.success());

        PipelineOutcome outcome = run(invoker, dispatcher, "nearby | after 10t | damage amount=1");

        assertEquals(PipelineOutcome.Status.SKIPPED, outcome.status(),
                "losing the target during a delay is a gameplay state, not an error");
        assertTrue(invoker.invocationLog().stream().noneMatch(entry -> entry.startsWith("damage")),
                "the deferred stage must not run against a dead target: " + invoker.invocationLog());
    }

    @Test
    @DisplayName("a timing stage with nothing after it is skipped rather than silently succeeding")
    void timingWithoutBodySkips() {
        CountingDispatcher dispatcher = new CountingDispatcher();
        FakeStageInvoker invoker = timingInvoker();

        PipelineOutcome outcome = run(invoker, dispatcher, "after 10t");

        assertEquals(PipelineOutcome.Status.SKIPPED, outcome.status());
        assertEquals("action.v2.run.timing_without_body", outcome.reasonKey());
    }

    @Test
    @DisplayName("a disabled owner during the delay stops the repeat loop")
    void ownerDisabledDuringDelayStops() {
        CountingDispatcher dispatcher = new CountingDispatcher();
        java.util.concurrent.atomic.AtomicBoolean enabled =
                new java.util.concurrent.atomic.AtomicBoolean(true);
        Plugin toggling = TestSubjects.plugin("Toggling", enabled);
        FakeStageInvoker invoker = timingInvoker()
                .action("tick", CoreTargetRequirement.NONE, (context, arguments) -> {
                    enabled.set(false);
                    return CoreActionOutcome.success();
                });
        ActionInterpreter interpreter = new ActionInterpreter(invoker, dispatcher.asStageDispatcher(),
                SequenceRepository.empty(), PipelineLimits.defaults());
        PipelineContext context = PipelineContext.root(toggling, TestSubjects.locatedCaster(), null,
                "test", false, null);
        String line = "every 20t times 5 | tick";
        CompiledPipeline pipeline = new CompiledPipeline(line, parser.parse(line).nodes(), false);

        interpreter.run(toggling, pipeline, context).join();

        assertEquals(1, invoker.invocationLog().size(),
                "once the owner is disabled the loop must stop instead of running all 6 iterations: "
                        + invoker.invocationLog());
    }
}
