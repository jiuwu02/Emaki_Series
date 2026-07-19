package emaki.jiuwu.craft.item.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ItemComponentInspectorTest {

    private final ItemComponentInspector inspector = new ItemComponentInspector();

    @Test
    void parsesStructuredAndScalarComponentValues() {
        var structured = inspector.parseComponentValue("{\"text\":\"Blade\",\"flags\":[true,2]}");
        assertTrue(structured.success());
        Map<?, ?> value = assertInstanceOf(Map.class, structured.value());
        assertEquals("Blade", value.get("text"));
        assertEquals(List.of(true, 2L), value.get("flags"));

        assertEquals("Blade", inspector.parseComponentValue("'Blade'").value());
        assertEquals(12L, inspector.parseComponentValue("12").value());
        assertEquals(0.5D, inspector.parseComponentValue("0.5").value());
        assertEquals(true, inspector.parseComponentValue("true").value());
        assertNull(inspector.parseComponentValue("null").value());
    }

    @Test
    void rejectsBlankAndMalformedStructuredValues() {
        var blank = inspector.parseComponentValue("  ");
        assertFalse(blank.success());
        assertEquals("Component value cannot be blank.", blank.errorMessage());

        var malformed = inspector.parseComponentValue("{\"text\":");
        assertFalse(malformed.success());
        assertEquals("Malformed structured component value.", malformed.errorMessage());
    }

    @Test
    void normalizesComponentIdsWithLocaleIndependentRules() {
        assertEquals("minecraft:custom_name", inspector.normalizeComponentId(" Custom Name "));
        assertEquals("minecraft:lore", inspector.normalizeComponentId("!LORE"));
        assertEquals("example:flag", inspector.normalizeComponentId("Example:Flag"));
    }
}
