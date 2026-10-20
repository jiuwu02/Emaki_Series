package emaki.jiuwu.craft.dungeon.drop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.mockbukkit.mockbukkit.MockBukkit;

import emaki.jiuwu.craft.dungeon.drop.DropAllocator.KindSpec;
import emaki.jiuwu.craft.dungeon.drop.DropAllocator.Planned;

class DropAllocatorTest {

    @BeforeAll
    static void setUp() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void singleKindSplitsByDescendingWeight() {
        List<Planned> planned = DropAllocator.plan(List.of(new KindSpec(10, 1)), 4);
        assertEquals(4, planned.size());
        assertEquals(4, planned.get(0).amount());
        assertEquals(3, planned.get(1).amount());
        assertEquals(2, planned.get(2).amount());
        assertEquals(1, planned.get(3).amount());
    }

    @Test
    void singleKindTwoMembersSplitThreeItems() {
        List<Planned> planned = DropAllocator.plan(List.of(new KindSpec(3, 1)), 2);
        assertEquals(2, planned.size());
        assertEquals(2, planned.get(0).amount());
        assertEquals(1, planned.get(1).amount());
    }

    @Test
    void singleKindRemainderGoesToHigherScore() {
        List<Planned> planned = DropAllocator.plan(List.of(new KindSpec(2, 1)), 3);
        assertEquals(1, planned.size());
        assertEquals(0, planned.get(0).ownerIndex());
        assertEquals(2, planned.get(0).amount());
    }

    @Test
    void moreKindsThanMembersRotatesAssignments() {
        List<KindSpec> kinds = List.of(new KindSpec(5, 3), new KindSpec(3, 2), new KindSpec(1, 1));
        List<Planned> planned = DropAllocator.plan(kinds, 2);
        assertEquals(3, planned.size());
        assertEquals(0, planned.get(0).ownerIndex());
        assertEquals(1, planned.get(1).ownerIndex());
        assertEquals(0, planned.get(2).ownerIndex());
        assertEquals(0, planned.get(0).kindIndex());
        assertEquals(1, planned.get(1).kindIndex());
        assertEquals(2, planned.get(2).kindIndex());
    }

    @Test
    void fewerKindsThanMembersLeavesLowestScoreEmpty() {
        List<KindSpec> kinds = List.of(new KindSpec(4, 1), new KindSpec(2, 1));
        List<Planned> planned = DropAllocator.plan(kinds, 3);
        assertEquals(2, planned.size());
        assertEquals(0, planned.get(0).ownerIndex());
        assertEquals(1, planned.get(1).ownerIndex());
        assertTrue(planned.stream().noneMatch(entry -> entry.ownerIndex() == 2));
    }

    @Test
    void sharePlanGivesEveryMemberEveryKind() {
        List<KindSpec> kinds = List.of(new KindSpec(7, 1), new KindSpec(2, 1));
        List<Planned> planned = DropAllocator.planShare(kinds, 3);
        assertEquals(6, planned.size());
        for (int owner = 0; owner < 3; owner++) {
            final int expected = owner;
            assertEquals(2, planned.stream().filter(entry -> entry.ownerIndex() == expected).count());
        }
        assertEquals(7, planned.get(0).amount());
        assertEquals(2, planned.get(1).amount());
    }

    @Test
    void allocateGivesHigherScoreTheBiggerStack() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Map<UUID, Double> scores = new LinkedHashMap<>();
        scores.put(alice, 10.0D);
        scores.put(bob, 5.0D);
        List<ItemStack> pool = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            pool.add(new ItemStack(Material.STONE));
        }
        Map<UUID, List<ItemStack>> result = DropAllocator.allocate(pool, scores, Map.of(),
                List.of(bob, alice));
        assertEquals(2, result.get(alice).get(0).getAmount());
        assertEquals(1, result.get(bob).get(0).getAmount());
    }

    @Test
    void allocateShareClonesForEveryMember() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        List<ItemStack> pool = new ArrayList<>();
        pool.add(new ItemStack(Material.STONE, 2));
        pool.add(new ItemStack(Material.DIRT, 1));
        Map<UUID, List<ItemStack>> result = DropAllocator.allocateShare(pool, List.of(alice, bob));
        assertEquals(2, result.get(alice).size());
        assertEquals(2, result.get(bob).size());
        int aliceTotal = result.get(alice).stream().mapToInt(ItemStack::getAmount).sum();
        assertEquals(3, aliceTotal);
    }

    @Test
    void emptyInputsProduceEmptyPlans() {
        assertTrue(DropAllocator.plan(null, 2).isEmpty());
        assertTrue(DropAllocator.plan(List.of(), 2).isEmpty());
        assertTrue(DropAllocator.plan(List.of(new KindSpec(1, 1)), 0).isEmpty());
        assertTrue(DropAllocator.planShare(List.of(), 2).isEmpty());
    }
}
