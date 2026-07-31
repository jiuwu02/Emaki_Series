package emaki.jiuwu.craft.corelib.action.v2.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;

/**
 * Covers decision D4: {@code times} defaults to 0, the default cap is 100, exceeding it is rejected
 * rather than silently truncated, and the cap is configurable.
 */
class RepeatLimitTest {

    private final PipelineParser parser = new PipelineParser();

    private StaticValidator.Result validate(String line, PipelineLimits limits) {
        FakeStageResolver resolver = new FakeStageResolver()
                .source("self")
                .gate("every",
                        CoreStageParameter.positional("interval", CoreStageParameterType.TIME, ""),
                        CoreStageParameter.optional("times", CoreStageParameterType.INTEGER, "0", ""))
                .action("damage", CoreStageParameter.required("amount", CoreStageParameterType.DOUBLE, ""));
        PipelineParser.Result parsed = parser.parse(line);
        return new StaticValidator(resolver, SequenceCatalog.empty(), limits)
                .validate(line, parsed.nodes(), PhaseContract.permissive("test"));
    }

    private static List<String> keys(StaticValidator.Result result) {
        return result.diagnostics().stream().map(CompileDiagnostic::reasonKey).toList();
    }

    @Test
    @DisplayName("omitting times leaves the declared default of 0")
    void timesDefaultsToZero() {
        StaticValidator.Result result = validate("self | every 20t | damage amount=1",
                PipelineLimits.defaults());

        assertTrue(result.successful(), () -> "unexpected diagnostics: " + keys(result));
        ActionAst.Stage every = (ActionAst.Stage) result.pipeline().nodes().get(1);
        assertEquals("20t", every.arguments().get("interval"));
        assertFalse(every.arguments().containsKey("times"),
                "times must stay absent so the declared default of 0 applies");
    }

    @Test
    @DisplayName("times at the cap is accepted")
    void timesAtCapAccepted() {
        StaticValidator.Result result = validate("self | every 20t times 100 | damage amount=1",
                PipelineLimits.defaults());

        assertTrue(result.successful(), () -> "unexpected diagnostics: " + keys(result));
        ActionAst.Stage every = (ActionAst.Stage) result.pipeline().nodes().get(1);
        assertEquals("100", every.arguments().get("times"));
    }

    @Test
    @DisplayName("times above the cap is rejected, not truncated")
    void timesAboveCapRejected() {
        StaticValidator.Result result = validate("self | every 20t times 100000 | damage amount=1",
                PipelineLimits.defaults());

        assertFalse(result.successful(), "a repeat count of 100000 must not compile");
        assertTrue(keys(result).contains("action.v2.validate.repeat_limit_exceeded"));
        assertEquals(100, result.diagnostics().get(0).detail().get("maximum"));
        assertEquals(100000, result.diagnostics().get(0).detail().get("value"));
    }

    @Test
    @DisplayName("the cap is configurable")
    void capIsConfigurable() {
        StaticValidator.Result raised = validate("self | every 20t times 500 | damage amount=1",
                PipelineLimits.withRepeatCap(1000));
        assertTrue(raised.successful(), () -> "unexpected diagnostics: " + keys(raised));

        StaticValidator.Result lowered = validate("self | every 20t times 5 | damage amount=1",
                PipelineLimits.withRepeatCap(3));
        assertFalse(lowered.successful(), "a cap of 3 must reject times=5");
        assertTrue(keys(lowered).contains("action.v2.validate.repeat_limit_exceeded"));
    }

    @Test
    @DisplayName("a non-numeric times value is rejected")
    void nonNumericTimesRejected() {
        StaticValidator.Result result = validate("self | every 20t times abc | damage amount=1",
                PipelineLimits.defaults());

        assertFalse(result.successful());
        assertTrue(keys(result).contains("action.v2.validate.invalid_repeat_times"));
    }

    @Test
    @DisplayName("a placeholder times value is rejected because the cap must be checkable at load time")
    void placeholderTimesRejected() {
        StaticValidator.Result result = validate("self | every 20t times %skill.level% | damage amount=1",
                PipelineLimits.defaults());

        assertFalse(result.successful(), "a runtime-resolved repeat count could exceed the cap unchecked");
        assertTrue(keys(result).contains("action.v2.validate.repeat_must_be_literal"));
    }

    @Test
    @DisplayName("a negative times value is rejected")
    void negativeTimesRejected() {
        StaticValidator.Result result = validate("self | every 20t times -1 | damage amount=1",
                PipelineLimits.defaults());

        assertFalse(result.successful());
        assertTrue(keys(result).contains("action.v2.validate.invalid_repeat_times"));
    }
}
