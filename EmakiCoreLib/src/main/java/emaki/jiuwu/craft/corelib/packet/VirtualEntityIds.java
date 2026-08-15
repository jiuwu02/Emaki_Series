package emaki.jiuwu.craft.corelib.packet;

import java.util.concurrent.atomic.AtomicInteger;

public final class VirtualEntityIds {

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1_000_000_000);

    private VirtualEntityIds() {
    }

    public static int next() {
        return NEXT_ID.getAndIncrement();
    }
}
