package emaki.jiuwu.craft.dungeon.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class InstanceSlotAllocatorTest {

    private static InstanceSlotAllocator allocator(int globalMax, int poolMax) {
        Function<String, Integer> poolMaxOf = dungeonId -> poolMax;
        return new InstanceSlotAllocator(globalMax, false, 10, poolMaxOf);
    }

    @Test
    void reserveRespectsGlobalCap() {
        InstanceSlotAllocator allocator = allocator(2, 5);
        assertNotNull(allocator.tryReservePool("d1", "emd_d1_a"));
        assertNotNull(allocator.tryReservePool("d1", "emd_d1_b"));
        assertNull(allocator.tryReservePool("d1", "emd_d1_c"));
        assertNull(allocator.tryReservePool("d2", "emd_d2_a"));
        assertEquals(2, allocator.globalWorlds());
        assertEquals(2, allocator.worldsOf("d1"));
        assertEquals(0, allocator.worldsOf("d2"));
    }

    @Test
    void reserveRespectsPerDungeonPoolCap() {
        InstanceSlotAllocator allocator = allocator(20, 1);
        assertNotNull(allocator.tryReservePool("d1", "emd_d1_a"));
        assertNull(allocator.tryReservePool("d1", "emd_d1_b"));
        assertNotNull(allocator.tryReservePool("d2", "emd_d2_a"));
        assertEquals(2, allocator.globalWorlds());
    }

    @Test
    void poolCapFallsBackToOneWhenUnconfigured() {
        InstanceSlotAllocator allocator = new InstanceSlotAllocator(20, false, 10, dungeonId -> null);
        assertNotNull(allocator.tryReservePool("d1", "emd_d1_a"));
        assertNull(allocator.tryReservePool("d1", "emd_d1_b"));
    }

    @Test
    void cancelPoolReleasesSlotExactlyOnce() {
        InstanceSlotAllocator allocator = allocator(5, 5);
        PoolReservation reservation = allocator.tryReservePool("d1", "emd_d1_a");
        assertNotNull(reservation);
        assertTrue(allocator.cancelPool(reservation));
        assertFalse(allocator.cancelPool(reservation));
        assertEquals(0, allocator.globalWorlds());
        assertNotNull(allocator.tryReservePool("d1", "emd_d1_a2"));
    }

    @Test
    void confirmSettlesReservationSoCancelBecomesNoOp() {
        InstanceSlotAllocator allocator = allocator(5, 5);
        PoolReservation reservation = allocator.tryReservePool("d1", "emd_d1_a");
        assertNotNull(reservation);
        assertTrue(allocator.confirmPool(reservation));
        assertFalse(allocator.cancelPool(reservation));
        assertEquals(1, allocator.globalWorlds());
        allocator.releaseWorld("d1");
        assertEquals(0, allocator.globalWorlds());
    }

    @Test
    void partyAcquireReportsDenyReasonsInPriorityOrder() {
        InstanceSlotAllocator allocator = allocator(2, 1);
        UUID partyA = UUID.randomUUID();
        UUID partyB = UUID.randomUUID();
        UUID partyC = UUID.randomUUID();
        assertNull(allocator.tryAcquireParty("d1", partyA, true));
        assertEquals(DenyReason.DENY_PARTY_BUSY, allocator.tryAcquireParty("d1", partyA, true));
        assertNotNull(allocator.tryReservePool("d1", "emd_d1_w1"));
        assertEquals(DenyReason.DENY_DUNGEON_FULL, allocator.tryAcquireParty("d1", partyB, false));
        assertNotNull(allocator.tryReservePool("d2", "emd_d2_w1"));
        assertEquals(DenyReason.DENY_GLOBAL_FULL, allocator.tryAcquireParty("d2", partyC, true));
        allocator.releaseParty(partyA);
        assertFalse(allocator.isPartyBusy(partyB));
        assertFalse(allocator.isPartyBusy(partyC));
    }

    @Test
    void heldWorldsOnFoliaBlockPartyAcquireAtCap() {
        InstanceSlotAllocator allocator = new InstanceSlotAllocator(20, true, 2, dungeonId -> 5);
        allocator.markHeld("d1");
        allocator.markHeld("d1");
        assertEquals(DenyReason.DENY_GLOBAL_FULL,
                allocator.tryAcquireParty("d1", UUID.randomUUID(), true));
        assertEquals(2, allocator.globalHeldWorlds());
    }

    @Test
    void resetClearsAllCounters() {
        InstanceSlotAllocator allocator = allocator(5, 5);
        allocator.tryReservePool("d1", "emd_d1_a");
        UUID party = UUID.randomUUID();
        allocator.tryAcquireParty("d1", party, true);
        allocator.markHeld("d1");
        allocator.reset();
        assertEquals(0, allocator.globalWorlds());
        assertEquals(0, allocator.globalHeldWorlds());
        assertEquals(0, allocator.worldsOf("d1"));
        assertFalse(allocator.isPartyBusy(party));
    }
}
