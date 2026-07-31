package emaki.jiuwu.craft.corelib.action.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.action.v2.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.action.v2.compile.PhaseContract;
import emaki.jiuwu.craft.corelib.action.v2.compile.SequenceCatalog;
import emaki.jiuwu.craft.corelib.action.v2.compile.StageResolver;
import emaki.jiuwu.craft.corelib.action.v2.exec.StageDispatcher;
import emaki.jiuwu.craft.corelib.action.v2.exec.StageInvoker;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

/** Covers the engine seam: compile rejects bad text, and only a compiled pipeline reaches execution. */
class ActionEngineTest {

    private static final CoreStageParameter AMOUNT =
            CoreStageParameter.required("amount", CoreStageParameterType.DOUBLE, "damage amount");

    private ActionEngine engine() {
        return new ActionEngine(resolver(), invoker(), StageDispatcher.inline(), null, null);
    }

    private static StageResolver resolver() {
        return new StageResolver() {

            @Override
            public Resolution resolve(String id) {
                return switch (id == null ? "" : id) {
                    case "self" -> Resolution.found(CoreStageKind.SOURCE, List.of(), Set.of(),
                            CoreTargetRequirement.NONE, ExecutionDomain.SERVER_GLOBAL);
                    case "damage" -> Resolution.found(CoreStageKind.ACTION, List.of(AMOUNT), Set.of(),
                            CoreTargetRequirement.OPTIONAL, ExecutionDomain.ENTITY);
                    default -> Resolution.unknown();
                };
            }

            @Override
            public List<String> knownIds(CoreStageKind kind) {
                return kind == CoreStageKind.SOURCE ? List.of("self") : List.of("damage");
            }
        };
    }

    private static StageInvoker invoker() {
        return new StageInvoker() {

            @Override
            public Handle resolve(String id) {
                return switch (id == null ? "" : id) {
                    case "self" -> new Handle("self", CoreStageKind.SOURCE, List.of(),
                            CoreTargetRequirement.NONE, 30_000L);
                    case "damage" -> new Handle("damage", CoreStageKind.ACTION, List.of(AMOUNT),
                            CoreTargetRequirement.OPTIONAL, 30_000L);
                    default -> null;
                };
            }

            @Override
            public ExecutionDomain domainOf(Handle handle,
                    CoreStageContext context,
                    CoreActionSubject target,
                    java.util.Map<String, String> rawArguments) {
                return ExecutionDomain.SERVER_GLOBAL;
            }

            @Override
            public CoreSourceResult invokeSource(Handle handle,
                    CoreStageContext context,
                    CoreResolvedArguments arguments) {
                return CoreSourceResult.selected(List.of());
            }

            @Override
            public CoreGateResult invokeGate(Handle handle,
                    CoreStageContext context,
                    List<CoreActionSubject> inbound,
                    CoreResolvedArguments arguments) {
                return CoreGateResult.passed(inbound);
            }

            @Override
            public CoreActionOutcome invokeAction(Handle handle,
                    CoreStageContext context,
                    CoreResolvedArguments arguments) {
                return arguments.getDouble("amount", -1D) == 10D
                        ? CoreActionOutcome.success()
                        : CoreActionOutcome.failure(null, "arguments_not_resolved");
            }
        };
    }

    @Test
    @DisplayName("a valid line compiles once and then runs without reparsing")
    void compileThenRun() {
        ActionEngine engine = engine();

        ActionEngine.Result compiled = engine.compile("self | damage amount=10",
                PhaseContract.permissive("test"));

        assertTrue(compiled.successful(), () -> "unexpected diagnostics: " + compiled.diagnostics());
        assertNotNull(compiled.pipeline());
        assertEquals("self | damage amount=10", compiled.pipeline().source());
    }

    @Test
    @DisplayName("an unclosed bracket is rejected at compile time")
    void unclosedBracketRejected() {
        ActionEngine.Result compiled = engine().compile("self | if %a%>1 [ damage amount=5",
                PhaseContract.permissive("test"));

        assertFalse(compiled.successful(), "YAML keeps this line intact, so only the compiler can catch it");
        assertFalse(compiled.diagnostics().isEmpty());
    }

    @Test
    @DisplayName("an unknown stage is rejected at compile time, before any execution")
    void unknownStageRejected() {
        ActionEngine.Result compiled = engine().compile("self | teleport", PhaseContract.permissive("test"));

        assertFalse(compiled.successful());
        assertTrue(compiled.diagnostics().stream()
                .map(CompileDiagnostic::reasonKey)
                .anyMatch("action.v2.validate.unknown_stage"::equals));
    }

    @Test
    @DisplayName("a blank line is rejected rather than compiling to an empty pipeline")
    void blankLineRejected() {
        ActionEngine.Result compiled = engine().compile("   ", PhaseContract.permissive("test"));

        assertFalse(compiled.successful());
        assertEquals("action.v2.validate.empty_pipeline", compiled.diagnostics().get(0).reasonKey());
    }

    @Test
    @DisplayName("the compiled pipeline records the implicit self source")
    void implicitSelfRecorded() {
        ActionEngine.Result compiled = engine().compile("damage amount=10", PhaseContract.permissive("test"));

        assertTrue(compiled.successful(), () -> "unexpected diagnostics: " + compiled.diagnostics());
        assertTrue(compiled.pipeline().implicitSelfSource(),
                "omitting the source must be recorded as an implicit self, matching v1 semantics");
    }

    @Test
    @DisplayName("an unsatisfied context key is rejected using the phase contract")
    void unsatisfiedContextRejected() {
        CoreActionKey<String> key = CoreActionKey.of("item_id", String.class);
        StageResolver strict = new StageResolver() {

            @Override
            public Resolution resolve(String id) {
                return "needs_item".equals(id)
                        ? Resolution.found(CoreStageKind.ACTION, List.of(), Set.of(key),
                                CoreTargetRequirement.NONE, ExecutionDomain.SERVER_GLOBAL)
                        : Resolution.unknown();
            }

            @Override
            public List<String> knownIds(CoreStageKind kind) {
                return List.of("needs_item");
            }
        };
        ActionEngine strictEngine = new ActionEngine(strict, invoker(), StageDispatcher.inline(),
                null, null);

        ActionEngine.Result compiled = strictEngine.compile("needs_item",
                PhaseContract.declared("give", Set.of(), Set.of(), false));

        assertFalse(compiled.successful());
        assertTrue(compiled.diagnostics().stream()
                .map(CompileDiagnostic::reasonKey)
                .anyMatch("action.v2.validate.missing_context_key"::equals));
    }

    @Test
    @DisplayName("a timing stage compiled through the engine still delays, using named arguments")
    void compiledTimingStageDelays() {
        // The other timing tests build the AST directly and therefore exercise the positional fallback.
        // This one goes through compile(), where the validator has already named the bare value, so the
        // named-argument path is covered too.
        CoreStageParameter delay = CoreStageParameter.positional("delay",
                CoreStageParameterType.TIME, "delay before the following stages");
        List<String> dispatched = new java.util.ArrayList<>();
        StageResolver timingResolver = new StageResolver() {

            @Override
            public Resolution resolve(String id) {
                return switch (id == null ? "" : id) {
                    // Omitting the source implies self, so the validator requires it to exist.
                    case "self" -> Resolution.found(CoreStageKind.SOURCE, List.of(), Set.of(),
                            CoreTargetRequirement.NONE, ExecutionDomain.SERVER_GLOBAL);
                    case "after" -> Resolution.found(CoreStageKind.GATE, List.of(delay), Set.of(),
                            CoreTargetRequirement.NONE, ExecutionDomain.ASYNC_COMPUTE);
                    case "broadcast" -> Resolution.found(CoreStageKind.ACTION, List.of(), Set.of(),
                            CoreTargetRequirement.NONE, ExecutionDomain.SERVER_GLOBAL);
                    default -> Resolution.unknown();
                };
            }

            @Override
            public List<String> knownIds(CoreStageKind kind) {
                return switch (kind) {
                    case SOURCE -> List.of("self");
                    case GATE -> List.of("after");
                    case ACTION -> List.of("broadcast");
                };
            }
        };
        StageInvoker timingInvoker = new StageInvoker() {

            @Override
            public Handle resolve(String id) {
                return switch (id == null ? "" : id) {
                    case "self" -> new Handle("self", CoreStageKind.SOURCE, List.of(),
                            CoreTargetRequirement.NONE, 30_000L);
                    case "after" -> new Handle("after", CoreStageKind.GATE, List.of(delay),
                            CoreTargetRequirement.NONE, 30_000L, true);
                    case "broadcast" -> new Handle("broadcast", CoreStageKind.ACTION, List.of(),
                            CoreTargetRequirement.NONE, 30_000L);
                    default -> null;
                };
            }

            @Override
            public ExecutionDomain domainOf(Handle handle,
                    CoreStageContext context,
                    CoreActionSubject target,
                    java.util.Map<String, String> rawArguments) {
                return ExecutionDomain.SERVER_GLOBAL;
            }

            @Override
            public CoreSourceResult invokeSource(Handle handle,
                    CoreStageContext context,
                    CoreResolvedArguments arguments) {
                return CoreSourceResult.selected(List.of());
            }

            @Override
            public CoreGateResult invokeGate(Handle handle,
                    CoreStageContext context,
                    List<CoreActionSubject> inbound,
                    CoreResolvedArguments arguments) {
                return CoreGateResult.passed(inbound);
            }

            @Override
            public CoreActionOutcome invokeAction(Handle handle,
                    CoreStageContext context,
                    CoreResolvedArguments arguments) {
                dispatched.add(handle.id());
                return CoreActionOutcome.success();
            }
        };

        List<String> observed = new java.util.ArrayList<>();
        ActionEngine timingEngine = new ActionEngine(timingResolver, timingInvoker,
                StageDispatcher.counting(observed::add), null, null);
        ActionEngine.Result compiled = timingEngine.compile("after 10t | broadcast",
                PhaseContract.permissive("test"));
        assertTrue(compiled.successful(), () -> "unexpected diagnostics: " + compiled.diagnostics());

        org.bukkit.plugin.Plugin owner = TimingOwner.enabled();
        timingEngine.run(owner, compiled.pipeline(),
                PipelineContext.root(owner, CoreActionSubject.absent(), null, "test", false, null)).join();

        assertTrue(observed.contains("delay@10"),
                "the compiled form names the bare value, and the delay must survive that: " + observed);
        assertEquals(List.of("broadcast"), dispatched);
    }

    /** Minimal enabled-plugin stand-in, kept local so this test needs no server. */
    private static final class TimingOwner {

        private static org.bukkit.plugin.Plugin enabled() {
            return (org.bukkit.plugin.Plugin) java.lang.reflect.Proxy.newProxyInstance(
                    ActionEngineTest.class.getClassLoader(),
                    new Class<?>[] {org.bukkit.plugin.Plugin.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isEnabled" -> true;
                        case "getName", "toString" -> "TimingOwner";
                        case "equals" -> proxy == (args == null ? null : args[0]);
                        case "hashCode" -> 1;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    @Test
    @DisplayName("the engine accepts an empty sequence catalog without treating run as valid")
    void emptyCatalogRejectsRun() {
        ActionEngine engineWithNoSequences = new ActionEngine(resolver(), invoker(),
                StageDispatcher.inline(), null, null);
        assertNotNull(SequenceCatalog.empty());

        ActionEngine.Result compiled = engineWithNoSequences.compile("self | run burst",
                PhaseContract.permissive("test"));

        assertFalse(compiled.successful());
        assertTrue(compiled.diagnostics().stream()
                .map(CompileDiagnostic::reasonKey)
                .anyMatch("action.v2.validate.unknown_sequence"::equals));
    }
}
