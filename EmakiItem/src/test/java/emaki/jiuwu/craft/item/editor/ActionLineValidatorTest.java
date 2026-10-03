package emaki.jiuwu.craft.item.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.action.descriptor.CoreActionStageDescriptor;

class ActionLineValidatorTest {

    private static final CoreActionStageDescriptor DAMAGE = new CoreActionStageDescriptor(
            "damage",
            CoreStageKind.ACTION,
            "core",
            "entity",
            "",
            "1",
            List.of(
                    CoreStageParameter.required("amount", CoreStageParameterType.DOUBLE, ""),
                    CoreStageParameter.optional("type", CoreStageParameterType.STRING, "physical", "")),
            CoreTargetRequirement.REQUIRED_ENTITY,
            Set.of(),
            Set.of(),
            Set.of());

    private static final CoreActionStageDescriptor RUN = new CoreActionStageDescriptor(
            "run",
            CoreStageKind.GATE,
            "core",
            "task",
            "",
            "1",
            List.of(CoreStageParameter.positional("sequence", CoreStageParameterType.STRING, "")),
            CoreTargetRequirement.NONE,
            Set.of(),
            Set.of(),
            Set.of());

    private final ActionLineValidator validator = new ActionLineValidator(resolver());

    private static Function<String, Optional<CoreActionStageDescriptor>> resolver() {
        Map<String, CoreActionStageDescriptor> stages = Map.of("damage", DAMAGE, "run", RUN);
        return id -> Optional.ofNullable(stages.get(id));
    }

    @Test
    void blankLineIsRejected() {
        assertEquals(ActionLineValidator.EMPTY, validator.validate(null));
        assertEquals(ActionLineValidator.EMPTY, validator.validate("   "));
    }

    @Test
    void emptyPipelineSegmentIsRejected() {
        assertEquals(ActionLineValidator.EMPTY_SEGMENT, validator.validate("damage amount=5 | "));
    }

    @Test
    void unknownStageIdIsRejected() {
        assertEquals(ActionLineValidator.UNKNOWN_STAGE, validator.validate("nope amount=5"));
    }

    @Test
    void unknownArgumentNameIsRejected() {
        assertEquals(ActionLineValidator.UNKNOWN_ARGUMENT, validator.validate("damage amount=5 colour=red"));
    }

    @Test
    void missingRequiredArgumentIsRejected() {
        assertEquals(ActionLineValidator.MISSING_ARGUMENT, validator.validate("damage type=physical"));
    }

    @Test
    void validLineWithOptionalArgumentPasses() {
        assertNull(validator.validate("damage amount=5 type=spell"));
    }

    @Test
    void barePositionalValueDoesNotTriggerMissingArgument() {
        assertNull(validator.validate("run 1 + 1"));
    }

    @Test
    void controlKeywordsAreAlwaysAccepted() {
        assertNull(validator.validate("select nearby"));
        assertNull(validator.validate("filter 'health <= 5'"));
    }

    @Test
    void multiSegmentPipelineValidatesEverySegment() {
        assertEquals(ActionLineValidator.UNKNOWN_STAGE, validator.validate("damage amount=5 | bogus x=1"));
        assertNull(validator.validate("damage amount=5 | damage amount=3 type=spell"));
    }

    @Test
    void argumentNamesAreCaseInsensitive() {
        assertNull(validator.validate("damage AMOUNT=5 TYPE=spell"));
    }
}
