package emaki.jiuwu.craft.skills.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.action.v2.exec.PipelineOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.skills.model.CastAttemptResult;

/**
 * Pins the pipeline-failure to player-message mapping in {@code CastAttemptService}.
 *
 * <p>Worth testing because the whole point of the mapping is that a server owner can tell a configuration
 * mistake apart from a runtime one; if two kinds silently collapsed onto {@code cast.skill_execute_failed} the
 * build would still pass and the diagnosis would be gone. The expectations below restate the phase 4 decision
 * table so a later edit to the switch has to disagree with something explicit.</p>
 *
 * <p>Temporary asset for phase 4 verification.</p>
 */
class CastFailureMappingTest {

    @Test
    void everyFailureKindMapsToItsPlannedMessageKey() {
        assertEquals("cast.script_invalid_argument",
                CastAttemptService.messageKeyFor(CoreActionFailureKind.INVALID_CONFIG, ""));
        assertEquals("cast.script_action_not_found",
                CastAttemptService.messageKeyFor(CoreActionFailureKind.MISSING_CONTEXT, ""));
        assertEquals("cast.script_timeout",
                CastAttemptService.messageKeyFor(CoreActionFailureKind.TIMEOUT, ""));
        assertEquals("cast.cancelled",
                CastAttemptService.messageKeyFor(CoreActionFailureKind.OWNER_DISABLED, ""));
        assertEquals("cast.skill_execute_failed",
                CastAttemptService.messageKeyFor(CoreActionFailureKind.WRONG_THREAD, ""));
        assertEquals("cast.skill_execute_failed",
                CastAttemptService.messageKeyFor(CoreActionFailureKind.REJECTED, ""));
        assertEquals("cast.skill_execute_failed",
                CastAttemptService.messageKeyFor(CoreActionFailureKind.INTERNAL_ERROR, ""));
    }

    @Test
    void theMappingCoversEveryKindTheEnumDeclares() {
        // Guards against a new CoreActionFailureKind silently falling through: the switch is exhaustive, so a
        // missing case would not compile, and this loop proves none of them yields a blank key.
        for (CoreActionFailureKind kind : CoreActionFailureKind.values()) {
            String key = CastAttemptService.messageKeyFor(kind, "");
            assertTrue(key.startsWith("cast."), () -> kind + " mapped to " + key);
        }
    }

    @Test
    void anAbsentMythicSkillIsRecognisedByItsReasonKeyNotItsKind() {
        // It shares INVALID_CONFIG with every other configuration mistake, so only the reason key can tell it
        // apart, and only its own reason lets the message name the missing skill.
        assertEquals("cast.mythic_not_found", CastAttemptService.messageKeyFor(
                CoreActionFailureKind.INVALID_CONFIG, CastAttemptService.MYTHIC_MISSING_REASON));
        assertEquals(CastAttemptResult.FailureReason.MYTHIC_SKILL_NOT_FOUND,
                CastAttemptService.failureReasonFor(CoreActionFailureKind.INVALID_CONFIG,
                        CastAttemptService.MYTHIC_MISSING_REASON));
    }

    @Test
    void ownerDisabledReportsCancelledRatherThanAFailedCast() {
        assertEquals(CastAttemptResult.FailureReason.CANCELLED,
                CastAttemptService.failureReasonFor(CoreActionFailureKind.OWNER_DISABLED, ""));
        assertEquals(CastAttemptResult.FailureReason.MYTHIC_CAST_FAILED,
                CastAttemptService.failureReasonFor(CoreActionFailureKind.INTERNAL_ERROR, ""));
    }

    @Test
    void onlyConfigurationMistakesAreLogged() {
        // A normal failed cast must not write to the console, otherwise a busy server floods its log with
        // gameplay outcomes.
        assertTrue(CastAttemptService.isConfigurationError(CoreActionFailureKind.INVALID_CONFIG));
        assertTrue(CastAttemptService.isConfigurationError(CoreActionFailureKind.MISSING_CONTEXT));
        assertTrue(CastAttemptService.isConfigurationError(CoreActionFailureKind.TIMEOUT));
        assertFalse(CastAttemptService.isConfigurationError(CoreActionFailureKind.OWNER_DISABLED));
        assertFalse(CastAttemptService.isConfigurationError(CoreActionFailureKind.REJECTED));
        assertFalse(CastAttemptService.isConfigurationError(CoreActionFailureKind.WRONG_THREAD));
        assertFalse(CastAttemptService.isConfigurationError(CoreActionFailureKind.INTERNAL_ERROR));
    }

    @Test
    void skippedAndPartialAreNotFailures() {
        // "No enemy was in range" and "three of five targets resisted" are gameplay results. Treating them as
        // failures would consume no resources and show an error for a cast that visibly happened.
        assertFalse(PipelineOutcome.skipped("action.v2.run.no_target", List.of()).status()
                == PipelineOutcome.Status.FAILURE);
        assertFalse(PipelineOutcome.partial("action.v2.run.partial_targets", Map.of(), List.of()).status()
                == PipelineOutcome.Status.FAILURE);
        assertTrue(PipelineOutcome.failure(CoreActionFailureKind.INVALID_CONFIG, "x", Map.of(), List.of())
                .status() == PipelineOutcome.Status.FAILURE);
    }
}
