package emaki.jiuwu.craft.dungeon.orchestration;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PoolReservation {

    private final String dungeonId;
    private final String worldName;
    private final AtomicBoolean settled = new AtomicBoolean(false);

    PoolReservation(String dungeonId, String worldName) {
        this.dungeonId = dungeonId;
        this.worldName = worldName;
    }

    public String dungeonId() {
        return dungeonId;
    }

    public String worldName() {
        return worldName;
    }

    public boolean markSettled() {
        return settled.compareAndSet(false, true);
    }
}
