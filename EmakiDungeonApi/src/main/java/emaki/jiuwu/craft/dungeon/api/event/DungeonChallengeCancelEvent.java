package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.HandlerList;

/**
 * Completion event fired when an active challenge session is aborted and
 * removed.
 *
 * <p>This is a completion event: the session has already been removed from
 * the challenge service and its UI cleaned up when listeners run. The event
 * does not implement {@link org.bukkit.event.Cancellable}; cancelling is not
 * observed.
 *
 * <p>Fired synchronously on the thread that aborted the session: the
 * global challenge tick timer, the command or GUI callback that requested
 * cancellation, or the entry path that failed its checks.
 *
 * <p>Coverage is challenge-wide: one event per aborted session, carrying
 * the dungeon id and a machine-readable abort reason such as
 * {@code window_timeout} or {@code challenge_start_cancelled}. It carries
 * no instance id because an aborted session never reached an instance.
 *
 * <p>Not fired when the session is no longer the active one for its party,
 * so repeated abort requests for the same session fire only once.
 */
public class DungeonChallengeCancelEvent extends org.bukkit.event.Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String dungeonId;
    private final String reason;

    /**
     * Creates a challenge cancel event.
     *
     * @param dungeonId the dungeon the aborted session was starting for
     * @param reason the abort reason; {@code null} is normalized to empty
     */
    public DungeonChallengeCancelEvent(String dungeonId, String reason) {
        this.dungeonId = dungeonId;
        this.reason = reason == null ? "" : reason;
    }

    /** {@return the id of the dungeon the aborted session was starting for} */
    public String getDungeonId() {
        return dungeonId;
    }

    /** {@return the abort reason; empty when it was unknown} */
    public String getReason() {
        return reason;
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
