package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.HandlerList;

/**
 * Completion event fired when a run reaches one of its authored endings.
 *
 * <p>This is a completion event: the ending is decided, and settlement of
 * the instance begins right after listeners run. The event does not
 * implement {@link org.bukkit.event.Cancellable}; cancelling is not
 * observed.
 *
 * <p>Fired synchronously on the thread that reached the ending: the server
 * execution thread that dispatched the confirmed option pick, or the thread
 * running the script call that requested the ending.
 *
 * <p>Coverage is instance-wide: one event per reached ending, carrying the
 * ending id and the option case that selected it.
 *
 * <p>Not fired when the option's ending map resolves no ending id or no
 * ending handler is bound.
 */
public class DungeonEndingEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String endingId;
    private final String optionCase;

    /**
     * Creates an ending event for one reached ending.
     *
     * @param instanceId the instance that reached the ending
     * @param dungeonId the dungeon definition the instance ran
     * @param endingId the reached ending id; {@code null} becomes empty
     * @param optionCase the option case that selected the ending;
     *         {@code null} becomes empty
     */
    public DungeonEndingEvent(String instanceId, String dungeonId, String endingId, String optionCase) {
        super(instanceId, dungeonId);
        this.endingId = endingId == null ? "" : endingId;
        this.optionCase = optionCase == null ? "" : optionCase;
    }

    /** {@return the reached ending id; empty when unknown} */
    public String getEndingId() {
        return endingId;
    }

    /** {@return the option case that selected the ending; empty when unknown} */
    public String getOptionCase() {
        return optionCase;
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
