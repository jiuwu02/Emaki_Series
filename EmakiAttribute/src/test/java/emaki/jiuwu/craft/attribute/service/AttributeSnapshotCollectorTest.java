package emaki.jiuwu.craft.attribute.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;

class AttributeSnapshotCollectorTest {

    @Test
    void keepsLegacyAdditiveBehaviorWhenMatchModeIsDisabled() {
        Map<String, Double> values = AttributeSnapshotCollector.resolveItemSourceValues(
                Map.of("physical_attack", 10D),
                Map.of("physical_attack", 10D),
                true,
                true,
                false
        );

        assertEquals(20D, values.get("physical_attack"));
    }

    @Test
    void countsMatchingLoreAndPdcAttributeOnce() {
        Map<String, Double> values = AttributeSnapshotCollector.resolveItemSourceValues(
                Map.of("physical_attack", 10D),
                Map.of("physical_attack", 10D),
                true,
                true,
                true
        );

        assertEquals(Map.of("physical_attack", 10D), values);
    }

    @Test
    void rejectsMismatchedOrSingleSourceAttributes() {
        Map<String, Double> values = AttributeSnapshotCollector.resolveItemSourceValues(
                Map.of("physical_attack", 10D, "physical_defense", 5D),
                Map.of("physical_attack", 8D, "spell_attack", 5D),
                true,
                true,
                true
        );

        assertTrue(values.isEmpty());
    }

    @Test
    void requiresRangeSpreadToMatchAsPartOfTheAttributeValue() {
        String spreadKey = AttributeSnapshot.rangeSpreadKey("physical_attack");
        Map<String, Double> matched = AttributeSnapshotCollector.resolveItemSourceValues(
                Map.of("physical_attack", 10D, spreadKey, 5D),
                Map.of("physical_attack", 10D, spreadKey, 5D),
                true,
                true,
                true
        );
        Map<String, Double> mismatched = AttributeSnapshotCollector.resolveItemSourceValues(
                Map.of("physical_attack", 10D, spreadKey, 5D),
                Map.of("physical_attack", 10D, spreadKey, 4D),
                true,
                true,
                true
        );

        assertEquals(Map.of("physical_attack", 10D, spreadKey, 5D), matched);
        assertTrue(mismatched.isEmpty());
    }

    @Test
    void matchModeFailsClosedWhenEitherSourceIsDisabled() {
        Map<String, Double> values = AttributeSnapshotCollector.resolveItemSourceValues(
                Map.of("physical_attack", 10D),
                Map.of("physical_attack", 10D),
                true,
                false,
                true
        );

        assertTrue(values.isEmpty());
    }
}
