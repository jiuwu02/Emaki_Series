package emaki.jiuwu.craft.codex.script;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggerScriptLogicTest {

    @Test
    void normalizeIdTrimsStringsAndRejectsOtherTypes() {
        assertEquals("hunter", TriggerScriptLogic.normalizeId(" hunter "));
        assertEquals("", TriggerScriptLogic.normalizeId("   "));
        assertEquals("", TriggerScriptLogic.normalizeId(null));
        assertEquals("", TriggerScriptLogic.normalizeId(42));
    }

    @Test
    void validatePayloadRejectsBlankId() {
        assertEquals("id_blank", TriggerScriptLogic.validatePayload("  ", null, true));
        assertEquals("id_blank", TriggerScriptLogic.validatePayload(null, null, true));
        assertEquals("id_blank", TriggerScriptLogic.validatePayload(42, null, true));
    }

    @Test
    void validatePayloadRejectsMissingAdvancements() {
        assertEquals("advancements_missing", TriggerScriptLogic.validatePayload("hunter", null, false));
    }

    @Test
    void validatePayloadRejectsInvalidPriority() {
        assertEquals("priority_invalid", TriggerScriptLogic.validatePayload("hunter", "fast", true));
        assertEquals("priority_invalid", TriggerScriptLogic.validatePayload("hunter", List.of(), true));
    }

    @Test
    void validatePayloadAcceptsValidPayload() {
        assertNull(TriggerScriptLogic.validatePayload(" hunter ", "20", true));
        assertNull(TriggerScriptLogic.validatePayload("hunter", null, true));
    }

    @Test
    void resolvePriorityDefaultsTo100WhenAbsentOrBlank() {
        assertEquals(100, TriggerScriptLogic.resolvePriority(null));
        assertEquals(100, TriggerScriptLogic.resolvePriority("   "));
    }

    @Test
    void resolvePriorityAcceptsNumbersAndNumericStrings() {
        assertEquals(42, TriggerScriptLogic.resolvePriority(42));
        assertEquals(42, TriggerScriptLogic.resolvePriority(42.7));
        assertEquals(7, TriggerScriptLogic.resolvePriority(" 7 "));
        assertEquals(-5, TriggerScriptLogic.resolvePriority(-5));
    }

    @Test
    void collectStringIdsKeepsStringsAndSkipsOtherElements() {
        assertEquals(List.of("a", "b"),
                TriggerScriptLogic.collectStringIds(Arrays.asList("a", 1, null, "b")));
        assertTrue(TriggerScriptLogic.collectStringIds(List.of(1, 2)).isEmpty());
        assertTrue(TriggerScriptLogic.collectStringIds(List.of()).isEmpty());
        assertTrue(TriggerScriptLogic.collectStringIds(null).isEmpty());
    }
}
