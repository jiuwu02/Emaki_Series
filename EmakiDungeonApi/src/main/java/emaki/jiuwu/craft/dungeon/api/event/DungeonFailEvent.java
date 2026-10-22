package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.HandlerList;

/**
 * Completion event fired when an instance fails, e.g. every member died or
 * the run timer expired.
 *
 * <p>This is a completion event: the failure has been accepted and teardown
 * is scheduled when listeners run. The event does not implement
 * {@link org.bukkit.event.Cancellable}; cancelling is not observed.
 *
 * <p>Fired synchronously on the thread that detected the failure: the
 * instance event loop for death and crash reports, the global failure tick
 * timer for timeouts, or the flow or script dispatch that requested the
 * failure.
 *
 * <p>Coverage is instance-wide and fires at most once: later fail requests
 * for the same instance are ignored. The payload carries the mapped
 * {@link Reason} and the raw reason string.
 *
 * <p>Not fired when the instance has no registered life state, is already
 * settling after a completion, or was already marked failed.
 */
public class DungeonFailEvent extends DungeonEvent {

    /**
     * Coarse cause of an instance failure, mapped from the raw reason
     * string reported by the implementation.
     */
    public enum Reason {
        /** Every party member has exhausted their lives. */
        ALL_DEAD,
        /** The instance run timer expired. */
        TIMEOUT,
        /** A flow action or script requested the failure. */
        SCRIPTED,
        /** The instance event loop crashed with an unexpected error. */
        CRASH,
        /** Any unrecognized reason string. */
        OTHER
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Reason reason;
    private final String detail;

    /**
     * Creates a failure event for one instance.
     *
     * @param instanceId the failing instance
     * @param dungeonId the dungeon definition the instance ran
     * @param reason the mapped failure cause
     * @param detail the raw reason string; {@code null} becomes empty
     */
    public DungeonFailEvent(String instanceId, String dungeonId, Reason reason, String detail) {
        super(instanceId, dungeonId);
        this.reason = reason;
        this.detail = detail == null ? "" : detail;
    }

    /** {@return the mapped failure cause} */
    public Reason getReason() {
        return reason;
    }

    /** {@return the raw reason string; empty when it was unknown} */
    public String getDetail() {
        return detail;
    }

    /** {@return the handlers of this event} */
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** {@return the static handler list for this event type} */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
