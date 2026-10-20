package emaki.jiuwu.craft.dungeon.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class WorldCommandQueueTest {

    private static WorldCommand cost(int blockCost) {
        return new WorldCommand() {
            @Override
            public int blockCost() {
                return blockCost;
            }

            @Override
            public void execute() {
            }
        };
    }

    @Test
    void drainCapsCommandCountPerTick() {
        WorldCommandQueue queue = new WorldCommandQueue(total -> {
        });
        for (int i = 0; i < 300; i++) {
            queue.submit(cost(0));
        }
        List<WorldCommand> batch = queue.drain();
        assertEquals(256, batch.size());
        assertEquals(44, queue.pending());
        assertTrue(queue.deferredTotal() > 0L);
    }

    @Test
    void drainCapsBlockCostPerTickWithFirstCommandExemption() {
        WorldCommandQueue queue = new WorldCommandQueue(total -> {
        });
        queue.submit(cost(8192));
        queue.submit(cost(1));
        List<WorldCommand> first = queue.drain();
        assertEquals(1, first.size());
        assertEquals(1, queue.pending());

        WorldCommandQueue second = new WorldCommandQueue(total -> {
        });
        second.submit(cost(5000));
        second.submit(cost(4000));
        List<WorldCommand> batch = second.drain();
        assertEquals(1, batch.size());
        assertEquals(1, second.pending());
    }

    @Test
    void backlogAlertFiresEveryHundredDeferredCommands() {
        List<Long> alerts = new ArrayList<>();
        WorldCommandQueue queue = new WorldCommandQueue(alerts::add);
        for (int i = 0; i < 500; i++) {
            queue.submit(cost(0));
        }
        List<WorldCommand> batch = queue.drain();
        assertEquals(256, batch.size());
        assertEquals(244, queue.pending());
        assertTrue(queue.deferredTotal() > 0L);
        assertFalse(alerts.isEmpty());
        assertTrue(alerts.get(0) >= 100L);
        queue.clear();
        assertEquals(0, queue.pending());
        queue.drain();
        assertFalse(queue.isBackpressured());
    }

    @Test
    void backpressureMarksAfterSustainedPending() {
        WorldCommandQueue queue = new WorldCommandQueue(total -> {
        });
        for (int i = 0; i < 30; i++) {
            queue.submit(cost(0));
            queue.drain();
            queue.submit(cost(1_000_000));
        }
        assertTrue(queue.isBackpressured());
    }

    @Test
    void drainInvokesCommandAndFailsafeKeepsQueueUsable() throws Exception {
        AtomicLong executed = new AtomicLong();
        WorldCommandQueue queue = new WorldCommandQueue(total -> {
        });
        queue.submit(new WorldCommand() {
            @Override
            public int blockCost() {
                return 0;
            }

            @Override
            public void execute() {
                executed.incrementAndGet();
            }
        });
        List<WorldCommand> batch = queue.drain();
        assertEquals(1, batch.size());
        for (WorldCommand command : batch) {
            command.execute();
        }
        assertEquals(1, executed.get());
        assertEquals(0, queue.pending());
    }
}
