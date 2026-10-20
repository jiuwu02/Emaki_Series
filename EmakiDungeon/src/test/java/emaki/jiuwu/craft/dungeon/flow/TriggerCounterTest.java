package emaki.jiuwu.craft.dungeon.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.dungeon.flow.FlowModules.Head;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.TriggerLimits;

class TriggerCounterTest {

    private static Head head(String id, int perTrigger, int perPlayer, int total) {
        return new Head(id, true, new TriggerLimits(perTrigger, perPlayer, total));
    }

    @Test
    void disabledTriggerIsAlwaysExhausted() {
        TriggerCounter counter = new TriggerCounter();
        Head disabled = new Head("g", false, new TriggerLimits(0, 0, 0));
        assertEquals(TriggerCounter.ExhaustedTier.PER_TRIGGER, counter.check(disabled, null));
        assertFalse(counter.consume(disabled, null));
    }

    @Test
    void perTriggerTierStopsAtLimit() {
        TriggerCounter counter = new TriggerCounter();
        Head limit = head("t1", 2, 0, 0);
        assertTrue(counter.consume(limit, null));
        assertTrue(counter.consume(limit, null));
        assertEquals(TriggerCounter.ExhaustedTier.PER_TRIGGER, counter.check(limit, null));
        assertFalse(counter.consume(limit, null));
    }

    @Test
    void perPlayerTierIsIndependentAcrossPlayers() {
        TriggerCounter counter = new TriggerCounter();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Head limit = head("t2", 0, 1, 0);
        assertTrue(counter.consume(limit, alice));
        assertEquals(TriggerCounter.ExhaustedTier.PER_PLAYER, counter.check(limit, alice));
        assertTrue(counter.consume(limit, bob));
        assertEquals(TriggerCounter.ExhaustedTier.PER_PLAYER, counter.check(limit, bob));
        assertEquals(TriggerCounter.ExhaustedTier.NONE, counter.check(limit, UUID.randomUUID()));
    }

    @Test
    void totalTierIsSharedAcrossPlayers() {
        TriggerCounter counter = new TriggerCounter();
        Head limit = head("t3", 0, 0, 3);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        assertTrue(counter.consume(limit, alice));
        assertTrue(counter.consume(limit, bob));
        assertTrue(counter.consume(limit, alice));
        assertEquals(TriggerCounter.ExhaustedTier.TOTAL, counter.check(limit, bob));
        assertFalse(counter.consume(limit, bob));
    }

    @Test
    void unlimitedTriggerNeverExhausts() {
        TriggerCounter counter = new TriggerCounter();
        Head limit = head("t4", 0, 0, 0);
        for (int i = 0; i < 100; i++) {
            assertTrue(counter.consume(limit, null));
        }
        assertEquals(TriggerCounter.ExhaustedTier.NONE, counter.check(limit, null));
        assertEquals(Integer.MAX_VALUE, counter.remaining(limit, null));
    }

    @Test
    void remainingReportsMinOfActiveTiers() {
        TriggerCounter counter = new TriggerCounter();
        UUID alice = UUID.randomUUID();
        Head limit = head("t5", 3, 2, 4);
        assertEquals(4, counter.remaining(limit, alice));
        counter.consume(limit, alice);
        assertEquals(1, counter.remaining(limit, alice));
        counter.consume(limit, alice);
        assertEquals(0, counter.remaining(limit, alice));
        assertEquals(TriggerCounter.ExhaustedTier.PER_PLAYER, counter.check(limit, alice));
        assertEquals(1, counter.remaining(limit, UUID.randomUUID()));
    }

    @Test
    void resetClearsAllCounters() {
        TriggerCounter counter = new TriggerCounter();
        Head limit = head("t6", 1, 0, 0);
        assertTrue(counter.consume(limit, null));
        assertEquals(TriggerCounter.ExhaustedTier.PER_TRIGGER, counter.check(limit, null));
        counter.reset();
        assertTrue(counter.consume(limit, null));
    }
}
