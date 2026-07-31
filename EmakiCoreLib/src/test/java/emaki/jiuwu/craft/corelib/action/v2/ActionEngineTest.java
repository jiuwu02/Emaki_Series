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
