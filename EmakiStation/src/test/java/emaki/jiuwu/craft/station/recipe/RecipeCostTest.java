package emaki.jiuwu.craft.station.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public final class RecipeCostTest {

    @Test
    void noneCostNeverCharges() {
        RecipeCost cost = RecipeCost.none();
        assertFalse(cost.charges());
        assertEquals(0L, cost.totalFor(5L));
    }

    @Test
    void blankProviderOrZeroAmountNormalizesToNone() {
        assertFalse(new RecipeCost("", 10L).charges());
        assertFalse(new RecipeCost(RecipeCost.VAULT, 0L).charges());
        assertFalse(new RecipeCost(null, 10L).charges());
    }

    @Test
    void fromTokenAcceptsAliasesIgnoringCase() {
        assertEquals(new RecipeCost(RecipeCost.VAULT, 3L), RecipeCost.fromToken("Vault", 3L));
        assertEquals(new RecipeCost(RecipeCost.EXCELLENT, 3L), RecipeCost.fromToken("excellent", 3L));
        assertEquals(new RecipeCost(RecipeCost.EXCELLENT, 3L), RecipeCost.fromToken("ExcellentEconomy", 3L));
        assertEquals(RecipeCost.none(), RecipeCost.fromToken("  ", 3L));
    }

    @Test
    void fromTokenRejectsUnknownCurrency() {
        assertNull(RecipeCost.fromToken("banana", 3L));
    }

    @Test
    void totalForScalesByBatchAndSaturatesOnOverflow() {
        assertEquals(300L, new RecipeCost(RecipeCost.VAULT, 3L).totalFor(100L));
        assertEquals(3L, new RecipeCost(RecipeCost.VAULT, 3L).totalFor(0L), "batch below one stays at one");
        assertEquals(0L, RecipeCost.none().totalFor(5L));
        assertEquals(Long.MAX_VALUE, new RecipeCost(RecipeCost.VAULT, Long.MAX_VALUE / 2L).totalFor(3L));
        assertTrue(new RecipeCost(RecipeCost.VAULT, 1L).totalFor(Long.MAX_VALUE) > 0L);
    }
}
