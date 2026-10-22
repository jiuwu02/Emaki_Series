package emaki.jiuwu.craft.dungeon.api.event;

import java.util.Set;

import org.bukkit.event.HandlerList;

/**
 * Completion event fired once the player's pick for an option has been
 * finalized and recorded as a flow fact.
 *
 * <p>This is a completion event: the selection is committed and unchosen
 * cases are about to be sealed when listeners run. The event does not
 * implement {@link org.bukkit.event.Cancellable}; cancelling is not
 * observed.
 *
 * <p>Fired synchronously on the server execution thread that dispatched
 * the chat-input callback carrying the confirmed pick.
 *
 * <p>Coverage is option-wide: one event per confirmed pick, carrying the
 * option id and the full set of chosen case ids (every pick for
 * multi-select options).
 *
 * <p>Not fired when the option definition is unknown, the pick list is
 * empty, the player aborted, or a single-select prompt expired. A
 * multi-select confirmation that times out still fires with the picks made
 * so far.
 */
public class DungeonOptionConfirmEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String optionId;
    private final Set<String> selected;

    /**
     * Creates an option confirmation event.
     *
     * @param instanceId the instance the option belongs to
     * @param dungeonId the dungeon definition the option belongs to
     * @param optionId the confirmed option id; {@code null} becomes empty
     * @param selected the chosen case ids; {@code null} becomes empty
     */
    public DungeonOptionConfirmEvent(String instanceId, String dungeonId, String optionId, Set<String> selected) {
        super(instanceId, dungeonId);
        this.optionId = optionId == null ? "" : optionId;
        this.selected = selected == null ? Set.of() : Set.copyOf(selected);
    }

    /** {@return the confirmed option id; empty when it was unknown} */
    public String getOptionId() {
        return optionId;
    }

    /**
     * {@return the chosen case ids; immutable, and empty when none were
     * supplied}
     */
    public Set<String> getSelected() {
        return selected;
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
