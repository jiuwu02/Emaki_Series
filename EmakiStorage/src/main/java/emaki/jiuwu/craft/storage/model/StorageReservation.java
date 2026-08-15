package emaki.jiuwu.craft.storage.model;

import java.util.List;
import java.util.UUID;

public final class StorageReservation {

    private final UUID reservationId;
    private final long expiresAtMillis;
    private final List<Op> ops;

    public StorageReservation(UUID reservationId, long expiresAtMillis, List<Op> ops) {
        this.reservationId = reservationId;
        this.expiresAtMillis = expiresAtMillis;
        this.ops = ops == null ? List.of() : List.copyOf(ops);
    }

    public record Op(StorageKey key, long delta) {
    }

    public UUID reservationId() {
        return reservationId;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    public List<Op> ops() {
        return ops;
    }

    public boolean expired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    public long heldAmount(StorageKey key) {
        long held = 0L;
        for (Op op : ops) {
            if (op.delta() < 0L && op.key().equals(key)) {
                held += -op.delta();
            }
        }
        return held;
    }
}
