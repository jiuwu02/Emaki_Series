package emaki.jiuwu.craft.dungeon.api.event;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Completion event fired when a party member has been teleported into the
 * dungeon instance.
 *
 * <p>This is a completion event, not a preamble: the member is already
 * inside the instance when listeners run. The event does not implement
 * {@link org.bukkit.event.Cancellable}; cancelling is not observed.
 *
 * <p>Fired synchronously on the global server thread that runs the
 * challenge entry flow (the primary thread on Paper).
 *
 * <p>Coverage is per player: one event fires for each member teleported
 * into the instance, carrying that member's id together with the party id.
 *
 * <p>Not fired for members the entry loop skipped because they were no
 * longer online, and not fired before the instance world has been assigned.
 */
public class DungeonEnterEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final String partyId;

    /**
     * Creates an enter event for one member.
     *
     * @param instanceId the instance the member entered
     * @param dungeonId the dungeon definition being entered
     * @param player the entering member's id
     * @param partyId the id of the member's party, in text form
     */
    public DungeonEnterEvent(String instanceId, String dungeonId, UUID player, String partyId) {
        super(instanceId, dungeonId);
        this.player = player;
        this.partyId = partyId;
    }

    /** {@return the id of the member who entered} */
    public UUID getPlayer() {
        return player;
    }

    /** {@return the id of the member's party, in text form} */
    public String getPartyId() {
        return partyId;
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
