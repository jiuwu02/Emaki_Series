package emaki.jiuwu.craft.dungeon.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.LongConsumer;

public final class WorldCommandQueue {

    private static final int MAX_COMMANDS_PER_TICK = 256;
    private static final int MAX_BLOCKS_PER_TICK = 8192;
    private static final int BACKPRESSURE_TICKS = 20;
    private static final long ALERT_INTERVAL = 100L;

    private final BlockingQueue<WorldCommand> queue = new LinkedBlockingQueue<>();
    private final LongConsumer deferredAlert;

    private volatile int backlogTicks;
    private volatile long deferredTotal;
    private volatile long alertedTotal;

    public WorldCommandQueue(LongConsumer deferredAlert) {
        this.deferredAlert = deferredAlert;
    }

    public void submit(WorldCommand command) {
        if (command != null) {
            queue.offer(command);
        }
    }

    public List<WorldCommand> drain() {
        List<WorldCommand> batch = new ArrayList<>();
        int blocks = 0;
        while (batch.size() < MAX_COMMANDS_PER_TICK) {
            WorldCommand head = queue.peek();
            if (head == null) {
                break;
            }
            int cost = Math.max(0, head.blockCost());
            if (!batch.isEmpty() && blocks + cost > MAX_BLOCKS_PER_TICK) {
                break;
            }
            WorldCommand polled = queue.poll();
            if (polled == null) {
                break;
            }
            blocks += cost;
            batch.add(polled);
        }
        int remaining = queue.size();
        if (remaining > 0) {
            backlogTicks = Math.min(BACKPRESSURE_TICKS, backlogTicks + 1);
            deferredTotal += remaining;
            long total = deferredTotal;
            if (total - alertedTotal >= ALERT_INTERVAL) {
                alertedTotal = total;
                if (deferredAlert != null) {
                    deferredAlert.accept(total);
                }
            }
        } else {
            backlogTicks = 0;
        }
        return batch;
    }

    public boolean isBackpressured() {
        return backlogTicks >= BACKPRESSURE_TICKS;
    }

    public int pending() {
        return queue.size();
    }

    public long deferredTotal() {
        return deferredTotal;
    }

    public void clear() {
        queue.clear();
    }
}
