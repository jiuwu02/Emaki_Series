package emaki.jiuwu.craft.corelib.action.v2.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

/** Covers the load-time validation list from the runtime design, one failure path per check. */
class StaticValidatorTest {

    private static final CoreActionKey<String> ITEM_ID = CoreActionKey.of("item_id", String.class);

    private final PipelineParser parser = new PipelineParser();

    private StaticValidator.Result validate(String line, StageResolver resolver) {
        return validate(line, resolver, SequenceCatalog.empty(), PipelineLimits.defaults(),
                PhaseContract.permissive("test"));
    }

    private StaticValidator.Result validate(String line,
            StageResolver resolver,
            SequenceCatalog sequences,
            PipelineLimits limits,
            PhaseContract phase) {
        PipelineParser.Result parsed = parser.parse(line);
        assertNotNull(parsed.nodes(), "parser produced no nodes for: " + line);
        return new StaticValidator(resolver, sequences, limits).validate(line, parsed.nodes(), phase);
    }

    private static List<String> keys(StaticValidator.Result result) {
        return result.diagnostics().stream().map(CompileDiagnostic::reasonKey).toList();
    }

    @Test
    @DisplayName("a fully valid pipeline compiles")
    void validPipelineCompiles() {
        FakeStageResolver resolver = new FakeStageResolver()
                .source("nearby", CoreStageParameter.optional("radius", CoreStageParameterType.DOUBLE, "8", ""))
                .gate("limit", CoreStageParameter.positional("count", CoreStageParameterType.INTEGER, ""))
                .action("damage", CoreStageParameter.required("amount", CoreStageParameterType.DOUBLE, ""));

        StaticValidator.Result result = validate("nearby radius=8 | limit 3 | damage amount=10", resolver);

        assertTrue(result.successful(), () -> "unexpected diagnostics: " + keys(result));
        assertEquals(3, result.pipeline().nodes().size());
        assertFalse(result.pipeline().implicitSelfSource());
    }

    @Test
    @DisplayName("check 1: an unknown stage name is rejected with candidates")
    void unknownStageRejected() {
        StaticValidator.Result result = validate("damag amount=1", new FakeStageResolver().action("damage"));

        assertFalse(result.successful());
        assertTrue(keys(result).contains("action.v2.validate.unknown_stage"));
        assertTrue(result.diagnostics().get(0).candidates().contains("damage"));
    }

    @Test
    @DisplayName("check 1: a known stage whose owner is disabled reports the owner, not a typo")
    void disabledOwnerReported() {
        StaticValidator.Result result = validate("skill_damage amount=1",
                new FakeStageResolver().disabled("skill_damage", CoreStageKind.ACTION, "EmakiSkills"));

        assertFalse(result.successful());
        assertTrue(keys(result).contains("action.v2.validate.stage_owner_disabled"));
        assertEquals("EmakiSkills", result.diagnostics().get(0).detail().get("owner"));
    }

    @Test
    @DisplayName("check 2: a source after an action is rejected")
    void sourceAfterActionRejected() {
        FakeStageResolver resolver = new FakeStageResolver()
                .source("self")
                .source("nearby")
                .action("damage");

        StaticValidator.Result result = validate("self | damage | nearby", resolver);

        assertFalse(result.successful());
        assertTrue(keys(result).contains("action.v2.validate.source_after_action"));
    }

    @Test
    @DisplayName("check 2: two sources in one pipeline are rejected")
    void multipleSourcesRejected() {
        FakeStageResolver resolver = new FakeStageResolver().source("self").source("nearby").action("damage");

        StaticValidator.Result result = validate("self | nearby | damage", resolver);

        assertFalse(result.successful());
        assertTrue(keys(result).contains("action.v2.validate.multiple_sources"));
    }

    @Test
    @DisplayName("check 3: a missing required argument is rejected")
    void missingRequiredArgumentRejected() {
        FakeStageResolver resolver = new FakeStageResolver()
                .source("self")
                .action("damage", CoreStageParameter.required("amount", CoreStageParameterType.DOUBLE, ""));

        StaticValidator.Result result = validate("self | damage", resolver);

        assertFalse(result.successful());
        assertTrue(keys(result).contains("action.v2.validate.missing_required_argument"));
    }

    @Test
    @DisplayName("check 4: a non-placeholder literal of the wrong type is rejected")
    void wrongLiteralTypeRejected() {
        FakeStageResolver resolver = new FakeStageResolver()
                .source("self")
                .action("damage", CoreStageParameter.required("amount", CoreStageParameterType.DOUBLE, ""));

        StaticValidator.Result result = validate("self | damage amount=abc", resolver);

        assertFalse(result.successful());
        assertTrue(keys(result).contains("action.v2.validate.invalid_argument_type"));
    }

    @Test
    @DisplayName("check 4: a placeholder value is not type-checked at compile time")
    void placeholderValueAccepted() {
        FakeStageResolver resolver = new FakeStageResolver()
                .source("self")
                .action("damage", CoreStageParameter.required("amount", CoreStageParameterType.DOUBLE, ""));

        StaticValidator.Result result = validate("self | damage amount=%skill.level%", resolver);

        assertTrue(result.successful(), () -> "unexpected diagnostics: " + keys(result));
    }

    @Test
    @DisplayName("check 5: a required context key the phase does not provide is rejected")
    void unsatisfiedContextKeyRejected() {
        FakeStageResolver resolver = new FakeStageResolver()
                .source("self")
                .action("item_component", CoreTargetRequirement.OPTIONAL, ExecutionDomain.ENTITY,
                        Set.of(ITEM_ID));
        PhaseContract phase = PhaseContract.declared("give", Set.of(), Set.of(), false);

        StaticValidator.Result result = validate("self | item_component", resolver,
                SequenceCatalog.empty(), PipelineLimits.defaults(), phase);

        assertFalse(result.successful());
        assertTrue(keys(result).contains("action.v2.validate.missing_context_key"));
        assertEquals("item_id", result.diagnostics().get(0).detail().get("key"));
    }

    @Test
    @DisplayName("check 6: an unknown run target is rejected")
    void unknownSequenceRejected() {
        FakeStageResolver resolver = new FakeStageResolver().source("self");
        FakeSequenceCatalog sequences = new FakeSequenceCatalog().define("burst", Set.of(), Set.of());

        StaticValidator.Result result = validate("self | run typo", resolver, sequences,
                PipelineLimits.defaults(), PhaseContract.permissive("test"));

        assertFalse(result.successful());
        assertTrue(keys(result).contains("action.v2.validate.unknown_sequence"));
        assertTrue(result.diagnostics().get(0).candidates().contains("burst"));
    }

    @Test
    @DisplayName("check 6: a run target missing a declared parameter is rejected")
    void missingSequenceParameterRejected() {
        FakeStageResolver resolver = new FakeStageResolver().source("self");
        FakeSequenceCatalog sequences = new FakeSequenceCatalog().define("burst", Set.of("power"), Set.of());

        StaticValidator.Result result = validate("self | run burst", resolver, sequences,
                PipelineLimits.defaults(), PhaseContract.permissive("test"));

        assertFalse(result.successful());
        assertTrue(keys(result).contains("action.v2.validate.missing_sequence_parameter"));
    }

    @Test
    @DisplayName("check 8: a sequence cycle is rejected instead of recursing until the depth cap")
    void sequenceCycleRejected() {
        FakeStageResolver resolver = new FakeStageResolver().source("self");
        FakeSequenceCatalog sequences = new FakeSequenceCatalog()
                .define("a", Set.of(), Set.of("b"))
                .define("b", Set.of(), Set.of("a"));

        StaticValidator.Result result = validate("self | run a", resolver, sequences,
                PipelineLimits.defaults(), PhaseContract.permissive("test"));

        assertFalse(result.successful());
        assertTrue(keys(result).contains("action.v2.validate.sequence_cycle"));
    }

    @Test
    @DisplayName("check 8: an acyclic sequence chain is accepted")
    void acyclicSequenceAccepted() {
        FakeStageResolver resolver = new FakeStageResolver().source("self");
        FakeSequenceCatalog sequences = new FakeSequenceCatalog()
                .define("a", Set.of(), Set.of("b"))
                .define("b", Set.of(), Set.of());

        StaticValidator.Result result = validate("self | run a", resolver, sequences,
                PipelineLimits.defaults(), PhaseContract.permissive("test"));

        assertTrue(result.successful(), () -> "unexpected diagnostics: " + keys(result));
    }

    @Test
    @DisplayName("check 10: a stage that never declared a thread domain is rejected")
    void undeclaredThreadDomainRejected() {
        FakeStageResolver resolver = new FakeStageResolver().source("self").undeclaredDomain("mystery");

        StaticValidator.Result result = validate("self | mystery", resolver);

        assertFalse(result.successful());
        assertTrue(keys(result).contains("action.v2.validate.thread_domain_undeclared"));
    }

    @Test
    @DisplayName("check 10: an async stage that also demands a target is rejected")
    void asyncStageWithTargetRejected() {
        FakeStageResolver resolver = new FakeStageResolver()
                .source("self")
                .action("compute", CoreTargetRequirement.REQUIRED_ENTITY, ExecutionDomain.ASYNC_COMPUTE, Set.of());

        StaticValidator.Result result = validate("self | compute", resolver);

        assertFalse(result.successful());
        assertTrue(keys(result).contains("action.v2.validate.async_stage_requires_target"));
    }

    @Test
    @DisplayName("check 10: an async stage requiring no target is accepted")
    void asyncStageWithoutTargetAccepted() {
        FakeStageResolver resolver = new FakeStageResolver()
                .source("self")
                .action("compute", CoreTargetRequirement.NONE, ExecutionDomain.ASYNC_COMPUTE, Set.of());

        StaticValidator.Result result = validate("self | compute", resolver);

        assertTrue(result.successful(), () -> "unexpected diagnostics: " + keys(result));
    }

    @Test
    @DisplayName("check 5: the same key is accepted when the phase declares it")
    void satisfiedContextKeyAccepted() {
        FakeStageResolver resolver = new FakeStageResolver()
                .source("self")
                .action("item_component", CoreTargetRequirement.OPTIONAL, ExecutionDomain.ENTITY,
                        Set.of(ITEM_ID));
        PhaseContract phase = PhaseContract.declared("interact", Set.of(ITEM_ID), Set.of(), false);

        StaticValidator.Result result = validate("self | item_component", resolver,
                SequenceCatalog.empty(), PipelineLimits.defaults(), phase);

        assertTrue(result.successful(), () -> "unexpected diagnostics: " + keys(result));
    }
}
