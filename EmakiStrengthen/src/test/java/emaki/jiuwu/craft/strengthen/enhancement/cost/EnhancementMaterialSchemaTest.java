package emaki.jiuwu.craft.strengthen.enhancement.cost;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public final class EnhancementMaterialSchemaTest {

    @Test
    void acceptsSupportedSchemas() {
        assertTrue(EnhancementMaterialSchema.supported(EnhancementMaterialSchema.LEGACY_VERSION));
        assertTrue(EnhancementMaterialSchema.supported(EnhancementMaterialSchema.CANONICAL_VERSION));
    }

    @Test
    void rejectsUnsupportedSchemas() {
        assertFalse(EnhancementMaterialSchema.supported(0));
        assertFalse(EnhancementMaterialSchema.supported(3));
    }
}
