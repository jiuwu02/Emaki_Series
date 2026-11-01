package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.HandlerList;

/**
 * Completion event fired when a player leaves a dungeon instance and their
 * pre-entry state has been restored.
 *
 * <p>This is a completion event: inventory, temporary items, and position
 * are already rolled back when listeners run. The event does not implement
 * {@link org.bukkit.event.Cancellable}; cancelling is not observed.
 *
 * <p>Fired synchronously on the server thread that performed the exit
 * handling: the leave command, the completion settlement, or the failure
 * settlement.
 *
 * <p>Coverage is per player: one event per member who leaves, carrying the
 * player's name and the exit reason, such as {@code voluntary},
 * {@code complete}, or a {@code fail:} prefixed reason.
 *
 * <p>Not fired for a player who has no dungeon pointer, i.e. never entered
 * an instance or has already been restored.
 */
public class DungeonExitEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String player;
    private final String reason;

    /**
     * Creates an exit event for one player.
     *
     * @param instanceId the instance being left; the restored pointer
     * @param dungeonId the dungeon definition; empty when already unknown
     * @param player the leaving player's name
     * @param reason the exit reason; {@code null} is normalized to empty
     */
    public DungeonExitEvent(String instanceId, String dungeonId, String player, String reason) {
        super(instanceId, dungeonId);
        this.player = player;
        this.reason = reason == null ? "" : reason;
    }

    /** {@return the name of the player who left} */
    public String getPlayer() {
        return player;
    }

    /** {@return the exit reason; empty when it was unknown} */
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
