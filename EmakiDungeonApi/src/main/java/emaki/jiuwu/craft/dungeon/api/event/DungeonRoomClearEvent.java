package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.HandlerList;

public class DungeonRoomClearEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String roomId;

    public DungeonRoomClearEvent(String instanceId, String dungeonId, String roomId) {
        super(instanceId, dungeonId);
        this.roomId = roomId == null ? "" : roomId;
    }

    public String getRoomId() {
        return roomId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
