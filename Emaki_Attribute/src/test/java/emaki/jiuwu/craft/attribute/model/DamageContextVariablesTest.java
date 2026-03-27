package emaki.jiuwu.craft.attribute.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DamageContextVariablesTest {

    @Test
    void builderNormalizesKeysAndPreservesValues() {
        DamageContextVariables variables = DamageContextVariables.builder()
            .put("Source Damage", 12.5)
            .put("Damage Cause", "FIRE")
            .put("Nested Value", Map.of("Inner Key", "Inner Value"))
            .build();

        assertEquals(12.5D, variables.doubleValue("source_damage", 0D));
        assertEquals("FIRE", variables.string("damage_cause", ""));
        assertTrue(variables.contains("nested_value"));
        assertFalse(variables.isEmpty());
    }

    @Test
    void mergeKeepsExistingKeysAndAddsNewOnes() {
        DamageContextVariables left = DamageContextVariables.builder()
            .put("cause", "FALL")
            .build();
        DamageContextVariables right = DamageContextVariables.builder()
            .put("source_damage", 8D)
            .build();

        DamageContextVariables merged = left.merge(right);

        assertEquals("FALL", merged.string("cause", ""));
        assertEquals(8D, merged.doubleValue("source_damage", 0D));
    }
}
