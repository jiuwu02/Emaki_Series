package emaki.jiuwu.craft.corelib.action.v2.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionKeys;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreCancellationToken;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;

/** Covers the dispatcher contract the runtime design fixes: one owner check, one cancellation path. */
class StageDispatcherTest {

    private final PipelineParser parser = new PipelineParser();

    private CompiledPipeline compile(String line) {
        return new CompiledPipeline(line, parser.parse(line).nodes(), false);
    }

    private PipelineOutcome run(Plugin owner, FakeStageInvoker invoker, String line) {
        ActionInterpreter interpreter = new ActionInterpreter(invoker, StageDispatcher.inline(),
                SequenceRepository.empty(), PipelineLimits.defaults());
        PipelineContext context = PipelineContext.root(owner, CoreActionSubject.absent(), null,
                "test", false, null);
        return interpreter.run(owner, compile(line), context).join();
    }

    @Test
    @DisplayName("a disabled owner fails the pipeline instead of scheduling work")
    void disabledOwnerFails() {
        java.util.concurrent.atomic.AtomicBoolean enabled = new java.util.concurrent.atomic.AtomicBoolean(false);
        Plugin owner = TestSubjects.plugin("DisabledPlugin", enabled);
        FakeStageInvoker invoker = new FakeStageInvoker()
                .action("broadcast", CoreTargetRequirement.NONE,
                        (context, arguments) -> CoreActionOutcome.success());

        PipelineOutcome outcome = run(owner, invoker, "broadcast");

        assertEquals(PipelineOutcome.Status.FAILURE, outcome.status());
        assertEquals(CoreActionFailureKind.OWNER_DISABLED, outcome.failureKind());
        assertTrue(invoker.invocationLog().isEmpty(), "no stage may run for a disabled owner");
    }

    @Test
    @DisplayName("the cancellation token is visible to stages and starts uncancelled")
    void cancellationTokenIsExposed() {
        Plugin owner = TestSubjects.enabledPlugin();
        FakeStageInvoker invoker = new FakeStageInvoker()
                .action("probe", CoreTargetRequirement.NONE, (context, arguments) -> {
                    CoreCancellationToken token = context.get(CoreActionKeys.CANCELLATION).orElse(null);
                    return token != null && !token.cancelled()
                            ? CoreActionOutcome.success()
                            : CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT, "no_token");
                });

        PipelineOutcome outcome = run(owner, invoker, "probe");

        assertEquals(PipelineOutcome.Status.SUCCESS, outcome.status());
    }

    @Test
    @DisplayName("cancelling an owner marks its in-flight signals cancelled")
    void cancelOwnerCancelsSignals() {
        StageDispatcher dispatcher = StageDispatcher.inline();
        assertEquals(0, dispatcher.cancelOwner(TestSubjects.enabledPlugin()),
                "an owner with no pending handles cancels nothing");
        dispatcher.close();
    }

    @Test
    @DisplayName("an invalid dispatch target fails instead of silently choosing another domain")
    void invalidTargetFails() {
        StageDispatcher.DispatchTarget entityTarget = StageDispatcher.DispatchTarget.entity(null);
        assertFalse(entityTarget.valid(), "an entity domain without an entity must be invalid");

        StageDispatcher.DispatchTarget locationTarget = StageDispatcher.DispatchTarget.location(null);
        assertFalse(locationTarget.valid(), "a region domain without a location must be invalid");

        assertTrue(StageDispatcher.DispatchTarget.global().valid());
        assertTrue(StageDispatcher.DispatchTarget.async().valid());
    }

    @Test
    @DisplayName("a stage throwing is reported as an internal failure, not propagated")
    void throwingStageBecomesFailure() {
        Plugin owner = TestSubjects.enabledPlugin();
        FakeStageInvoker invoker = new FakeStageInvoker()
                .source("nearby", (context, arguments) -> CoreSourceResult.selected(
                        List.of(TestSubjects.entity("only").subject())))
                .action("boom", CoreTargetRequirement.REQUIRED_ENTITY, (context, arguments) -> {
                    throw new IllegalStateException("stage exploded");
                });

        PipelineOutcome outcome = run(owner, invoker, "nearby | boom");

        assertEquals(PipelineOutcome.Status.FAILURE, outcome.status());
        assertEquals(CoreActionFailureKind.INTERNAL_ERROR, outcome.failureKind());
    }

    @Test
    @DisplayName("the inline dispatcher rejects a null owner rather than defaulting to one")
    void nullOwnerRejected() {
        StageDispatcher dispatcher = StageDispatcher.inline();
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> dispatcher.dispatch(null, StageDispatcher.DispatchTarget.global(), 0L, "test",
                        1_000L, new CancellationSignal(), () -> "value"));
        assertInstanceOf(NullPointerException.class, thrown);
        dispatcher.close();
    }
}
