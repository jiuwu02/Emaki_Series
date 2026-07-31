package emaki.jiuwu.craft.corelib.action.builtin.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.action.v2.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;

/**
 * Contract checks for the builtin stage table.
 *
 * <p>Temporary asset for phase 3 verification, removed once the phase is accepted.</p>
 */
class BuiltinStageContractTest {

    @Test
    void registersEveryBuiltinStageWithoutFailure() {
        StageRegistry registry = new StageRegistry();
        BuiltinStages.Report report = BuiltinStages.registerAll(registry, null, null, null,
                null, null, null, null);

        assertTrue(report.successful(), () -> "registration failures: " + report.failures());
        Map<CoreStageKind, Integer> counts = registry.counts();
        assertEquals(BuiltinStages.SOURCE_COUNT, counts.get(CoreStageKind.SOURCE));
        assertEquals(BuiltinStages.GATE_COUNT, counts.get(CoreStageKind.GATE));
        assertEquals(BuiltinStages.ACTION_COUNT, counts.get(CoreStageKind.ACTION));
    }

    @Test
    void stageIdsAreUniqueAcrossAllThreeKinds() {
        StageRegistry registry = new StageRegistry();
        BuiltinStages.registerAll(registry, null, null, null, null, null, null, null);

        List<String> allIds = new ArrayList<>();
        registry.allIds().values().forEach(allIds::addAll);
        Set<String> unique = new HashSet<>(allIds);

        // A duplicate id across kinds would make `kindOf` ambiguous and let a pipeline resolve the wrong stage.
        assertEquals(allIds.size(), unique.size(),
                () -> "duplicate stage ids across kinds: " + allIds);
    }

    @Test
    void everyStageIdUsesLowerSnakeCase() {
        StageRegistry registry = new StageRegistry();
        BuiltinStages.registerAll(registry, null, null, null, null, null, null, null);

        registry.allIds().forEach((kind, ids) -> ids.forEach(id ->
                assertTrue(id.matches("[a-z][a-z0-9_]*"),
                        () -> kind + " stage id is not lower_snake_case: " + id)));
    }

    @Test
    void noStageLeavesItsThreadDomainUndeclared() {
        CoreStagePlanningContext probe = null;
        List<String> undeclared = new ArrayList<>();
        for (CoreActionSource source : BuiltinStageFixtures.sources()) {
            if (source.executionTarget(probe).domain() == CoreActionExecutionDomain.UNDECLARED) {
                undeclared.add(source.id());
            }
        }
        for (CoreActionStage stage : BuiltinStageFixtures.actions()) {
            if (stage.executionTarget(probe).domain() == CoreActionExecutionDomain.UNDECLARED) {
                undeclared.add(stage.id());
            }
        }
        assertTrue(undeclared.isEmpty(), () -> "stages with an undeclared domain: " + undeclared);
    }

    @Test
    void asyncStagesNeverRequireATarget() {
        // Registration enforces this rule; the test states why it matters. An async stage cannot touch a
        // Bukkit target, so requiring one would be a contradiction the registry is right to reject.
        for (CoreActionStage stage : BuiltinStageFixtures.actions()) {
            if (stage.executionTarget(null).domain() == CoreActionExecutionDomain.ASYNC_COMPUTE) {
                assertEquals(CoreTargetRequirement.NONE, stage.targetRequirement(),
                        () -> stage.id() + " is async but requires a target");
            }
        }
    }

    @Test
    void everyDeclaredParameterHasANonBlankNameAndDescription() {
        List<String> problems = new ArrayList<>();
        BuiltinStageFixtures.sources().forEach(source ->
                collectParameterProblems(source.id(), source.parameters(), problems));
        BuiltinStageFixtures.gates().forEach(gate ->
                collectParameterProblems(gate.id(), gate.parameters(), problems));
        BuiltinStageFixtures.actions().forEach(stage ->
                collectParameterProblems(stage.id(), stage.parameters(), problems));
        assertTrue(problems.isEmpty(), () -> "parameter declaration problems: " + problems);
    }

    @Test
    void requiredParametersDeclareNoDefault() {
        List<String> problems = new ArrayList<>();
        BuiltinStageFixtures.actions().forEach(stage -> stage.parameters().forEach(parameter -> {
            // A required parameter with a default is contradictory: the default would satisfy the requirement,
            // so the "required" marking could never fire.
            if (parameter.required() && !parameter.defaultValue().isBlank()) {
                problems.add(stage.id() + "." + parameter.name());
            }
        }));
        assertTrue(problems.isEmpty(), () -> "required parameters carrying defaults: " + problems);
    }

    @Test
    void gatesAndSourcesCarryUsefulDescriptions() {
        BuiltinStageFixtures.sources().forEach(source ->
                assertFalse(source.description().isBlank(), () -> source.id() + " has no description"));
        BuiltinStageFixtures.gates().forEach(gate ->
                assertFalse(gate.description().isBlank(), () -> gate.id() + " has no description"));
        BuiltinStageFixtures.actions().forEach(stage -> {
            assertFalse(stage.description().isBlank(), () -> stage.id() + " has no description");
            assertFalse(stage.category().isBlank(), () -> stage.id() + " has no category");
        });
    }

    @Test
    void reportNamesTheStageThatFailed() {
        StageRegistry registry = new StageRegistry();
        BuiltinStages.registerAll(registry, null, null, null, null, null, null, null);
        // Registering the same table twice must fail on every id rather than silently replacing entries.
        BuiltinStages.Report second = BuiltinStages.registerAll(registry, null, null, null,
                null, null, null, null);

        assertFalse(second.successful());
        assertEquals(BuiltinStages.SOURCE_COUNT + BuiltinStages.GATE_COUNT + BuiltinStages.ACTION_COUNT,
                second.failures().size());
        assertNotNull(second.failures().get(0));
        assertTrue(second.failures().stream().allMatch(failure -> failure.contains(":")),
                () -> "failure entries should read 'id: reasonKey': " + second.failures());
    }

    private static void collectParameterProblems(String stageId,
            List<CoreStageParameter> parameters,
            List<String> problems) {
        for (CoreStageParameter parameter : parameters) {
            if (parameter.name().isBlank()) {
                problems.add(stageId + ": blank parameter name");
            }
            if (parameter.description().isBlank()) {
                problems.add(stageId + "." + parameter.name() + ": blank description");
            }
        }
    }

    @Test
    void everyGateDeclaresAThreadNeed() {
        BuiltinStageFixtures.gates().forEach(gate ->
                assertNotNull(gate.threadNeed(), () -> gate.id() + " has no thread need"));
    }

    @Test
    void sourceStagesNeverRequireInboundTargets() {
        // A source produces the flow, so it must be usable as the first stage of a pipeline.
        for (CoreActionSource source : BuiltinStageFixtures.sources()) {
            assertNotNull(source.executionTarget(null), () -> source.id() + " has no execution target");
        }
    }

    @Test
    void gateIdsDoNotCollideWithActionIds() {
        Set<String> gateIds = new HashSet<>();
        BuiltinStageFixtures.gates().forEach(gate -> gateIds.add(gate.id()));
        for (CoreActionGate gate : BuiltinStageFixtures.gates()) {
            assertFalse(gate.id().isBlank(), "a gate has a blank id");
        }
        BuiltinStageFixtures.actions().forEach(stage ->
                assertFalse(gateIds.contains(stage.id()),
                        () -> "id " + stage.id() + " is registered as both a gate and an action"));
    }
}
