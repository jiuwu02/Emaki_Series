package emaki.jiuwu.craft.storage.model;

import java.util.List;
import java.util.UUID;

/**
 * One outstanding hold over a player's storage.
 *
 * <p>A reservation keeps the whole original op list, not just the withdrawal side. Commit has to
 * re-apply the deposits too, and rebuilding them from the held amounts would silently drop them.
 *
 * <p>{@code expiresAtMillis} is wall-clock rather than a tick count on purpose: the hold must survive
 * a restart, and tick counters reset while {@link System#currentTimeMillis()} does not.
 */
public final class StorageReservation {

    private final UUID reservationId;
    private final long expiresAtMillis;
    private final List<Op> ops;

    /**
     * @param reservationId   the reservation identity
     * @param expiresAtMillis wall-clock expiry, in epoch milliseconds
     * @param ops             the signed increments this reservation will apply on commit
     */
    public StorageReservation(UUID reservationId, long expiresAtMillis, List<Op> ops) {
        this.reservationId = reservationId;
        this.expiresAtMillis = expiresAtMillis;
        this.ops = ops == null ? List.of() : List.copyOf(ops);
    }

    /**
     * One signed increment inside a reservation.
     *
     * @param key   the stored item identity
     * @param delta signed unit count; negative withdraws, positive deposits
     */
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

    /**
     * {@return whether this hold has outlived its ttl}
     *
     * @param nowMillis the current wall-clock time
     */
    public boolean expired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    /**
     * {@return how many units of {@code key} this hold keeps out of circulation}
     *
     * @param key the stored item identity
     */
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
