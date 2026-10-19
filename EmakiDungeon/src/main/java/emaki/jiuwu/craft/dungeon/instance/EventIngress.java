package emaki.jiuwu.craft.dungeon.instance;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EventIngress {

    public record IngressEvent(String trigger, String sourceId, Map<String, Object> payload) {

        public static IngressEvent of(String trigger, String sourceId) {
            return new IngressEvent(trigger, sourceId, Map.of());
        }
    }

    private final BlockingQueue<IngressEvent> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public boolean post(IngressEvent event) {
        if (event == null || closed.get()) {
            return false;
        }
        return queue.offer(event);
    }

    public IngressEvent poll() {
        return queue.poll();
    }

    public IngressEvent poll(long timeoutMillis) throws InterruptedException {
        return queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public int size() {
        return queue.size();
    }

    public void close() {
        closed.set(true);
        queue.clear();
    }
}
