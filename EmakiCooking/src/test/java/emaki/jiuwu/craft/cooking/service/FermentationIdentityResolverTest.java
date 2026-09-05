package emaki.jiuwu.craft.cooking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FermentationIdentityResolverTest {

    @Test
    void uniqueMigrationPreservesSlotAndCountIdentity() {
        FermentationIdentityResolver.MigrationResult result = FermentationIdentityResolver.migrate(Map.of(
                10, List.of(new FermentationIdentityResolver.Identity("fruit_slot", "fruit")),
                11, List.of(new FermentationIdentityResolver.Identity("sweetener_slot", "sweetener"))));

        assertTrue(result.accepted());
        assertTrue(result.shouldWrite());
        assertEquals("fruit_slot", result.allocations().get(10).slotId());
        assertEquals("sweetener", result.allocations().get(11).countKey());
    }

    @Test
    void multipleMatcherCandidatesAreRejected() {
        FermentationIdentityResolver.MigrationResult result = FermentationIdentityResolver.migrate(Map.of(
                10, List.of(
                        new FermentationIdentityResolver.Identity("plain_apple", "fruit"),
                        new FermentationIdentityResolver.Identity("named_apple", "fruit"))));

        assertFalse(result.accepted());
        assertFalse(result.shouldWrite());
        assertTrue(result.allocations().isEmpty());
    }

    @Test
    void sameSourceWithDifferentMatchersCanResolveToDifferentSlots() {
        FermentationIdentityResolver.MigrationResult result = FermentationIdentityResolver.migrate(Map.of(
                10, List.of(new FermentationIdentityResolver.Identity("plain_apple", "fruit")),
                11, List.of(new FermentationIdentityResolver.Identity("named_apple", "premium_fruit"))));

        assertTrue(result.accepted());
        assertEquals("plain_apple", result.allocations().get(10).slotId());
        assertEquals("named_apple", result.allocations().get(11).slotId());
    }

    @Test
    void duplicateResolvedSlotsAreRejected() {
        FermentationIdentityResolver.MigrationResult result = FermentationIdentityResolver.migrate(Map.of(
                10, List.of(new FermentationIdentityResolver.Identity("fruit_slot", "fruit")),
                11, List.of(new FermentationIdentityResolver.Identity("fruit_slot", "fruit"))));

        assertFalse(result.accepted());
        assertFalse(result.shouldWrite());
    }

    @Test
    void countKeysAggregateAcrossStableSlots() {
        Map<String, Integer> totals = FermentationIdentityResolver.aggregate(
                Map.of(10, "fruit", 11, "fruit", 12, "sweetener"),
                Map.of(10, 2, 11, 3, 12, 1));

        assertEquals(Map.of("fruit", 5, "sweetener", 1), totals);
    }
}
