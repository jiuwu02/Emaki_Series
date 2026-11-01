package emaki.jiuwu.craft.dungeon.api.event;

import java.util.Set;

import org.bukkit.event.HandlerList;

/**
 * Completion event fired when an option confirmation seals the cases the
 * player did not pick.
 *
 * <p>This is a completion event: the unchosen cases have been resolved and
 * their regions are being sealed when listeners run. The event does not
 * implement {@link org.bukkit.event.Cancellable}; cancelling is not
 * observed.
 *
 * <p>Fired synchronously on the global server thread, from the queued
 * world command executed by the instance command pump after the pick was
 * confirmed.
 *
 * <p>Coverage is option-wide: one event per confirmation that sealed at
 * least one case, carrying the option id and the sealed case ids.
 *
 * <p>Not fired when every case was picked, because then nothing had to be
 * sealed.
 */
public class DungeonSealPathEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String optionId;
    private final Set<String> sealedCases;

    /**
     * Creates a seal path event for one option confirmation.
     *
     * @param instanceId the instance the option belongs to
     * @param dungeonId the dungeon definition the option belongs to
     * @param optionId the confirmed option id; {@code null} becomes empty
     * @param sealedCases the sealed case ids; {@code null} becomes empty
     */
    public DungeonSealPathEvent(String instanceId, String dungeonId, String optionId, Set<String> sealedCases) {
        super(instanceId, dungeonId);
        this.optionId = optionId == null ? "" : optionId;
        this.sealedCases = sealedCases == null ? Set.of() : Set.copyOf(sealedCases);
    }

    /** {@return the confirmed option id; empty when it was unknown} */
    public String getOptionId() {
        return optionId;
    }

    /**
     * {@return the case ids that were sealed; immutable, and empty when none
     * were supplied}
     */
    public Set<String> getSealedCases() {
        return sealedCases;
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
