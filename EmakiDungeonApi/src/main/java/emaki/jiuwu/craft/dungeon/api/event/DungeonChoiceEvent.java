package emaki.jiuwu.craft.dungeon.api.event;

import java.util.Set;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Preamble event fired when an option trigger asks the player to pick
 * among its cases.
 *
 * <p>This is a preamble event: no selection has been made yet. The event
 * implements {@link org.bukkit.event.Cancellable}; cancelling suppresses
 * the choice prompt entirely.
 *
 * <p>Fired synchronously on the thread dispatching the flow event that
 * triggered the option (the instance event loop) or the thread running the
 * script call that triggered it.
 *
 * <p>Coverage is option-wide: one event per option trigger, carrying the
 * option id, its select mode, and the full set of candidate case ids.
 *
 * <p>Not fired when the option definition is unknown, the player is
 * offline, the option has no cases, or the trigger conditions are not
 * adjudicated.
 */
public class DungeonChoiceEvent extends DungeonEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String optionId;
    private final String selectMode;
    private final Set<String> selected;
    private boolean cancelled;

    /**
     * Creates a choice event for one option trigger.
     *
     * @param instanceId the instance the option belongs to
     * @param dungeonId the dungeon definition the option belongs to
     * @param optionId the triggered option id; {@code null} becomes empty
     * @param selectMode the option's select mode; {@code null} becomes
     *         {@code single}
     * @param selected the candidate case ids; {@code null} becomes empty
     */
    public DungeonChoiceEvent(String instanceId, String dungeonId, String optionId,
            String selectMode, Set<String> selected) {
        super(instanceId, dungeonId);
        this.optionId = optionId == null ? "" : optionId;
        this.selectMode = selectMode == null ? "single" : selectMode;
        this.selected = selected == null ? Set.of() : Set.copyOf(selected);
    }

    /** {@return the triggered option id; empty when it was unknown} */
    public String getOptionId() {
        return optionId;
    }

    /** {@return the option's select mode, e.g. {@code single} or {@code multi}} */
    public String getSelectMode() {
        return selectMode;
    }

    /**
     * {@return the candidate case ids of the option; immutable, and empty
     * when none were supplied}
     */
    public Set<String> getSelected() {
        return selected;
    }

    /** {@return whether a listener has cancelled the option prompt} */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Cancels or uncancels the option trigger; a cancelled trigger suppresses
     * the choice prompt.
     *
     * @param cancel {@code true} to cancel the option trigger
     */
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
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
