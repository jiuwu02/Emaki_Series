package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.HandlerList;

/**
 * Completion event fired when the instance's room progress advances to a
 * room.
 *
 * <p>This is a completion event: the room has been entered in flow order
 * when listeners run. The event does not implement
 * {@link org.bukkit.event.Cancellable}; cancelling is not observed.
 *
 * <p>Fired synchronously on the thread dispatching the flow event that
 * reported the region entry (the instance event loop), or the thread
 * running a script's room entry call.
 *
 * <p>Coverage is room-wide: one event per room advance, carrying the room
 * id; the triggering player is not part of the payload.
 *
 * <p>Not fired when the room is unknown, the player has not entered its
 * region, the room does not immediately follow the current room in order,
 * or the runtime is under script takeover.
 */
public class DungeonRoomClearEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String roomId;

    /**
     * Creates a room clear event.
     *
     * @param instanceId the instance the room belongs to
     * @param dungeonId the dungeon definition the room belongs to
     * @param roomId the advanced-into room id; {@code null} becomes empty
     */
    public DungeonRoomClearEvent(String instanceId, String dungeonId, String roomId) {
        super(instanceId, dungeonId);
        this.roomId = roomId == null ? "" : roomId;
    }

    /** {@return the id of the room that was entered; empty when unknown} */
    public String getRoomId() {
        return roomId;
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
