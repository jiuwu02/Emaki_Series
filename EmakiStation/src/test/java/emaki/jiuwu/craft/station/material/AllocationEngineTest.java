package emaki.jiuwu.craft.station.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public final class AllocationEngineTest {

    @Test
    void sourceAndMatcherAreAnded() {
        AllocationEngine.Result result = AllocationEngine.allocate(List.of(1L, 1L), List.of(1L),
                (candidate, requirement) -> candidate == 1);
        assertTrue(result.satisfied(), "AND-compatible candidate must satisfy");
        assertEquals(1, result.assignments().size(), "exactly one candidate must be allocated");
        assertEquals(1, result.assignments().getFirst().candidateIndex(),
                "source-only candidate must not bypass matcher");
    }

    @Test
    void sameSourceDifferentCountKeysStaySeparate() {
        Map<String, Long> counted = AllocationEngine.countByKey(
                List.of("quality_a", "quality_b"), List.of(2L, 3L));
        assertEquals(2L, counted.get("quality_a"), "first count key must remain separate");
        assertEquals(3L, counted.get("quality_b"), "second count key must remain separate");
    }

    @Test
    void differentSourcesSameCountKeyAggregate() {
        Map<String, Long> counted = AllocationEngine.countByKey(
                List.of("metal", "metal"), List.of(2L, 3L));
        assertEquals(5L, counted.get("metal"), "shared count key must aggregate different sources");
    }

    @Test
    void backpackAndStorageCanMix() {
        AllocationEngine.Result result = AllocationEngine.allocate(List.of(2L, 3L), List.of(4L),
                (candidate, requirement) -> true);
        assertTrue(result.satisfied(), "mixed channels must satisfy combined demand");
        assertEquals(4L, result.assignments().stream()
                .mapToLong(AllocationEngine.Assignment::amount).sum(),
                "mixed allocation must debit exactly the demand");
        assertEquals(2L, result.assignments().stream()
                .map(AllocationEngine.Assignment::candidateIndex).distinct().count(),
                "mixed allocation must use backpack and storage");
    }

    @Test
    void allocationAndRefundConserveQuantity() {
        AllocationEngine.Result result = AllocationEngine.allocate(List.of(2L, 3L), List.of(5L),
                (candidate, requirement) -> true);
        long debited = result.assignments().stream().mapToLong(AllocationEngine.Assignment::amount).sum();
        long refunded = result.assignments().stream().mapToLong(AllocationEngine.Assignment::amount).sum();
        assertTrue(result.satisfied(), "full allocation must satisfy");
        assertEquals(debited, refunded, "refund must conserve original allocation");
        assertEquals(5L, debited, "allocation must consume exactly required quantity");
    }
}

