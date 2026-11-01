package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.HandlerList;

/**
 * Completion event fired once an instance has finished settlement.
 *
 * <p>This is a completion event: scoring is finished and members are about
 * to be returned to the main world when listeners run. The event does not
 * implement {@link org.bukkit.event.Cancellable}; cancelling is not
 * observed.
 *
 * <p>Fired synchronously on the global server thread, because settlement
 * is dispatched there through the execution dispatcher.
 *
 * <p>Coverage is instance-wide: one event per successful settlement,
 * carrying the total team score and the elapsed run time in milliseconds.
 *
 * <p>Not fired when the instance was no longer running as settlement began
 * (another completion or failure won the state transition), or when
 * settlement found no members to settle.
 */
public class DungeonCompleteEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final double score;
    private final long durationMs;

    /**
     * Creates a completion event for one settled instance.
     *
     * @param instanceId the settled instance
     * @param dungeonId the dungeon definition the instance ran
     * @param score the total team score of the run
     * @param durationMs the elapsed run time in milliseconds
     */
    public DungeonCompleteEvent(String instanceId, String dungeonId, double score, long durationMs) {
        super(instanceId, dungeonId);
        this.score = score;
        this.durationMs = durationMs;
    }

    /** {@return the total team score of the run} */
    public double getScore() {
        return score;
    }

    /** {@return the elapsed run time in milliseconds} */
    public long getDurationMs() {
        return durationMs;
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
